package com.chonbosmods.waypoint;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emits map/compass markers for active quest POIs. Runs on the WorldMap tick thread (~10Hz).
 *
 * Per quest POI:
 * - Center marker (QuestCenter.png with quest name): visible on compass + map, hides within 100b
 * - Ring of 48 markers (Quest.png): visible only when map UI is open, always visible on map
 *
 * The ECS Store enforces thread affinity so we read from a ConcurrentHashMap cache
 * populated by {@link #refreshMarkers} from the world thread.
 */
public class QuestMarkerProvider implements WorldMapManager.MarkerProvider {

    public static final QuestMarkerProvider INSTANCE = new QuestMarkerProvider();

    private static final double HIDE_RADIUS = 100.0;
    private static final double HIDE_RADIUS_SQ = HIDE_RADIUS * HIDE_RADIUS;
    private static final String CENTER_ICON = "QuestCenter.png";
    private static final String RETURN_ICON = "QuestReturn.png";
    private static final String TARGET_ICON = "QuestTarget.png";
    private static final String RING_ICON = "Quest.png";
    private static final int RING_COUNT = 48;
    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Waypoint");

    public enum MarkerType { POI, RETURN, TARGET_NPC }

    /** Marker data per player, written from world thread, read from map thread. */
    private final Map<UUID, List<MarkerEntry>> playerMarkers = new ConcurrentHashMap<>();

    private QuestMarkerProvider() {}

    /** Cached marker data for one quest. */
    public record MarkerEntry(String questId, String questName, double x, double z, MarkerType type) {}

    // Reflection handle for WorldMapTracker.clientHasWorldMapVisible
    private static final Field MAP_VISIBLE_FIELD;
    static {
        Field f = null;
        try {
            f = WorldMapTracker.class.getDeclaredField("clientHasWorldMapVisible");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOGGER.atWarning().log("Could not find clientHasWorldMapVisible field");
        }
        MAP_VISIBLE_FIELD = f;
    }

    private static boolean isMapOpen(Player player) {
        if (MAP_VISIBLE_FIELD == null) return true;
        try {
            return MAP_VISIBLE_FIELD.getBoolean(player.getWorldMapTracker());
        } catch (Exception e) {
            return true;
        }
    }

    public void updatePlayer(UUID playerUuid, List<MarkerEntry> markers) {
        if (markers == null || markers.isEmpty()) {
            playerMarkers.remove(playerUuid);
        } else {
            playerMarkers.put(playerUuid, List.copyOf(markers));
        }
    }

    public void removePlayer(UUID playerUuid) {
        playerMarkers.remove(playerUuid);
    }

    /**
     * Rebuild the marker cache for a player from their current quest data.
     * Call from the world thread after quest accept/complete/abandon.
     */
    public static void refreshMarkers(UUID playerUuid, Nat20PlayerData playerData) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        List<MarkerEntry> entries = new ArrayList<>();

        for (QuestInstance quest : quests.values()) {
            // Honor per-quest waypoint toggle (flipped from the Quest Log UI).
            if (!quest.isWaypointEnabled()) continue;
            Map<String, String> b = quest.getVariableBindings();
            // Return-to-NPC marker stays up through AWAITING_CONTINUATION so the player
            // can find their way back if they closed the dialog mid turn-in flow.
            com.chonbosmods.quest.QuestState qs = quest.getState();
            boolean objectivesComplete = qs == com.chonbosmods.quest.QuestState.READY_FOR_TURN_IN
                    || qs == com.chonbosmods.quest.QuestState.AWAITING_CONTINUATION;
            boolean hasPoi = "true".equals(b.get("poi_available"));
            // Use named POI subject as waypoint label, fall back to objective summary
            String questName = b.getOrDefault("subject_name",
                b.getOrDefault("quest_objective_summary",
                    b.getOrDefault("quest_title", quest.getSituationId())));

            if (objectivesComplete) {
                // Return marker at settlement origin
                SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
                if (settlements != null && quest.getSourceSettlementId() != null) {
                    SettlementRecord settlement = settlements.getByCell(quest.getSourceSettlementId());
                    if (settlement != null) {
                        entries.add(new MarkerEntry(quest.getQuestId(), questName,
                            settlement.getPosX(), settlement.getPosZ(), MarkerType.RETURN));
                    }
                }
            } else {
                // Check current objective type to decide marker style
                com.chonbosmods.quest.ObjectiveInstance currentObj = quest.getCurrentObjective();
                if (currentObj != null && !currentObj.isComplete()) {
                    com.chonbosmods.quest.ObjectiveType objType = currentObj.getType();

                    if (objType == com.chonbosmods.quest.ObjectiveType.TALK_TO_NPC) {
                        // Target NPC marker at their settlement
                        String targetSettlementKey = b.get("target_npc_settlement_key");
                        if (targetSettlementKey != null && b.containsKey("target_npc")) {
                            SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
                            if (settlements != null) {
                                SettlementRecord target = settlements.getByCell(targetSettlementKey);
                                if (target != null) {
                                    String targetLabel = "Speak with " + b.get("target_npc");
                                    entries.add(new MarkerEntry(quest.getQuestId(), targetLabel,
                                        target.getPosX(), target.getPosZ(), MarkerType.TARGET_NPC));
                                }
                            }
                        }
                    } else if (objType == com.chonbosmods.quest.ObjectiveType.PEACEFUL_FETCH) {
                        // Settlement marker at target settlement (same style as TALK_TO_NPC)
                        String locationId = currentObj.getLocationId();
                        if (locationId != null) {
                            SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
                            if (settlements != null) {
                                SettlementRecord target = settlements.getByCell(locationId);
                                if (target != null) {
                                    String fetchLabel = "Find " + currentObj.getTargetLabel();
                                    entries.add(new MarkerEntry(quest.getQuestId(), fetchLabel,
                                        target.getPosX(), target.getPosZ(), MarkerType.TARGET_NPC));
                                }
                            }
                        }
                    } else if (hasPoi) {
                        // POI marker at offset from center (KILL_MOBS, FETCH_ITEM)
                        String rawCx = b.get("poi_center_x");
                        String rawCz = b.get("poi_center_z");
                        String rawOx = b.get("marker_offset_x");
                        String rawOz = b.get("marker_offset_z");
                        if (rawCx != null && rawCz != null && rawOx != null && rawOz != null) {
                            try {
                                double mx = Double.parseDouble(rawCx) + Double.parseDouble(rawOx);
                                double mz = Double.parseDouble(rawCz) + Double.parseDouble(rawOz);
                                entries.add(new MarkerEntry(quest.getQuestId(), questName, mx, mz, MarkerType.POI));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }

        LOGGER.atFine().log("refreshMarkers: caching %d markers for player %s", entries.size(), playerUuid);
        INSTANCE.updatePlayer(playerUuid, entries);
    }

    @Override
    public void update(@Nonnull World world, @Nonnull Player player,
                       @Nonnull MarkersCollector collector) {
        try {
            List<MarkerEntry> markers = playerMarkers.get(player.getUuid());
            if (markers == null || markers.isEmpty()) return;

            PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
            if (playerRef == null) return;
            Transform playerTransform = playerRef.getTransform();
            if (playerTransform == null) return;
            Vector3d playerPos = playerTransform.getPosition();

            boolean mapOpen = isMapOpen(player);

            for (MarkerEntry entry : markers) {
                if (entry.type == MarkerType.RETURN) {
                    // Return marker: always visible on compass + map, no ring
                    collector.addIgnoreViewDistance(
                            new MapMarkerBuilder("nat20_return_" + entry.questId, RETURN_ICON,
                                    new Transform(new Vector3d(entry.x, playerPos.getY(), entry.z)))
                                    .withCustomName(entry.questName)
                                    .build());
                } else if (entry.type == MarkerType.TARGET_NPC) {
                    // Target NPC marker: always visible on compass + map, no ring
                    collector.addIgnoreViewDistance(
                            new MapMarkerBuilder("nat20_target_" + entry.questId, TARGET_ICON,
                                    new Transform(new Vector3d(entry.x, playerPos.getY(), entry.z)))
                                    .withCustomName(entry.questName)
                                    .build());
                } else {
                    // POI marker: center icon hides within radius, ring on map only
                    double dx = playerPos.getX() - entry.x;
                    double dz = playerPos.getZ() - entry.z;
                    boolean insideRadius = dx * dx + dz * dz <= HIDE_RADIUS_SQ;

                    if (!insideRadius) {
                        collector.addIgnoreViewDistance(
                                new MapMarkerBuilder("nat20_quest_" + entry.questId, CENTER_ICON,
                                        new Transform(new Vector3d(entry.x, playerPos.getY(), entry.z)))
                                        .withCustomName(entry.questName)
                                        .build());
                    }

                    if (mapOpen) {
                        for (int i = 0; i < RING_COUNT; i++) {
                            double angle = 2.0 * Math.PI * i / RING_COUNT;
                            double rx = entry.x + HIDE_RADIUS * Math.cos(angle);
                            double rz = entry.z + HIDE_RADIUS * Math.sin(angle);
                            collector.addIgnoreViewDistance(
                                    new MapMarkerBuilder("nat20_ring_" + entry.questId + "_" + i, RING_ICON,
                                            new Transform(new Vector3d(rx, playerPos.getY(), rz)))
                                            .withCustomName(".")
                                            .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error updating quest markers");
        }
    }
}
