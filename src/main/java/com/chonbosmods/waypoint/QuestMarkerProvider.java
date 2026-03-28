package com.chonbosmods.waypoint;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emits map/compass markers for active quest POIs. Runs on the WorldMap tick thread (~10Hz).
 *
 * Because the ECS Store enforces thread affinity (world thread only), we cannot read
 * Nat20PlayerData here. Instead, quest marker positions are cached via {@link #updatePlayer}
 * which must be called from the world thread whenever quest state changes.
 */
public class QuestMarkerProvider implements WorldMapManager.MarkerProvider {

    public static final QuestMarkerProvider INSTANCE = new QuestMarkerProvider();

    private static final double HIDE_RADIUS = 100.0;
    private static final double HIDE_RADIUS_SQ = HIDE_RADIUS * HIDE_RADIUS;
    private static final String MARKER_ICON = "Home.png";
    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Waypoint");

    /** Marker positions per player, written from world thread, read from map thread. */
    private final Map<UUID, List<MarkerEntry>> playerMarkers = new ConcurrentHashMap<>();

    private QuestMarkerProvider() {}

    /** A cached marker position for one quest. */
    public record MarkerEntry(String questId, double x, double z) {}

    /**
     * Update the cached marker list for a player. Call from the world thread
     * whenever quests are accepted, completed, or abandoned.
     */
    public void updatePlayer(UUID playerUuid, List<MarkerEntry> markers) {
        if (markers == null || markers.isEmpty()) {
            playerMarkers.remove(playerUuid);
        } else {
            playerMarkers.put(playerUuid, List.copyOf(markers));
        }
    }

    /** Remove a player's cached markers (e.g., on disconnect). */
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
            Map<String, String> b = quest.getVariableBindings();
            if (!"true".equals(b.get("poi_available"))) continue;

            String rawCx = b.get("poi_center_x");
            String rawCz = b.get("poi_center_z");
            String rawOx = b.get("marker_offset_x");
            String rawOz = b.get("marker_offset_z");
            if (rawCx == null || rawCz == null || rawOx == null || rawOz == null) continue;

            try {
                double mx = Double.parseDouble(rawCx) + Double.parseDouble(rawOx);
                double mz = Double.parseDouble(rawCz) + Double.parseDouble(rawOz);
                entries.add(new MarkerEntry(quest.getQuestId(), mx, mz));
            } catch (NumberFormatException ignored) {}
        }

        INSTANCE.updatePlayer(playerUuid, entries);
    }

    @Override
    public void update(@Nonnull World world, @Nonnull Player player,
                       @Nonnull MarkersCollector collector) {
        try {
            List<MarkerEntry> markers = playerMarkers.get(player.getUuid());
            if (markers == null || markers.isEmpty()) return;

            // PlayerRef.getTransform() is safe from any thread
            PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
            if (playerRef == null) return;
            Transform playerTransform = playerRef.getTransform();
            if (playerTransform == null) return;
            Vector3d playerPos = playerTransform.getPosition();

            for (MarkerEntry entry : markers) {
                double dx = playerPos.getX() - entry.x;
                double dz = playerPos.getZ() - entry.z;
                if (dx * dx + dz * dz <= HIDE_RADIUS_SQ) continue;

                collector.addIgnoreViewDistance(
                        new MapMarkerBuilder("nat20_quest_" + entry.questId, MARKER_ICON,
                                new Transform(new Vector3d(entry.x, playerPos.getY(), entry.z)))
                                .build());
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error updating quest markers");
        }
    }
}
