package com.chonbosmods.quest.poi;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.prefab.PlacedMarkers;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestStateManager;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared POI placement helpers used by both the procedural quest flow
 * (DialogueActionRegistry.resolveAndPlacePoi) and the tutorial quest flow
 * (TutorialPhase3Setup). Both paths need the same three operations once
 * a cave void is selected:
 *
 * <ol>
 *   <li>{@link #placePoiAtVoid}: paste the hostile-POI dungeon prefab into the
 *       claimed void (async) and, on success, call into finalizePlacement.</li>
 *   <li>{@link #finalizePlacement}: write poi_* bindings, serialise the scanned
 *       mob-group/chest marker positions, pick a random marker_offset, validate
 *       the populationSpec, save the quest, refresh the player's waypoints.</li>
 *   <li>{@link #serializeVec3dList}: semicolon-delimited int triples used by
 *       POIProximitySystem to scatter mobs at the marker cells.</li>
 * </ol>
 *
 * <p>Previously these were private methods on DialogueActionRegistry; the
 * tutorial path duplicated them. Extracting keeps both flows in lockstep.
 */
public final class PoiPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PoiPlacer");

    private PoiPlacer() {}

    /**
     * Place the hostile-POI dungeon at a claimed cave void. Writes the
     * {@code poi_available} / {@code poi_center_*} bindings synchronously, then
     * kicks off the async paste. On paste success, schedules
     * {@link #finalizePlacement} on the world thread to write the remaining
     * bindings + save the quest. On paste failure, flips {@code poi_available}
     * back to {@code "false"}.
     *
     * <p>Caller must have already claimed the void via
     * {@code CaveVoidRegistry.claimVoid} before invoking.
     */
    public static void placePoiAtVoid(QuestInstance quest, ObjectiveInstance objective,
                                      CaveVoidRecord void_,
                                      Store<EntityStore> store, Ref<EntityStore> playerRef) {
        Map<String, String> bindings = quest.getVariableBindings();
        bindings.put("poi_available", "true");
        bindings.put("poi_center_x", String.valueOf(void_.getCenterX()));
        bindings.put("poi_center_z", String.valueOf(void_.getCenterZ()));

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        Natural20.getInstance().getStructurePlacer()
            .placeAtVoid(world, void_, store)
            .whenComplete((placed, error) -> {
                if (error != null || placed == null) {
                    if (error != null) {
                        LOGGER.atWarning().withCause(error).log(
                            "POI void placement failed for quest %s", quest.getQuestId());
                    }
                    world.execute(() -> bindings.put("poi_available", "false"));
                    return;
                }
                Vector3i entrance = placed.anchorWorld();
                world.execute(() -> finalizePlacement(
                    quest, objective, entrance, placed, store, playerRef));
            });
    }

    /**
     * Shared post-placement finalisation: writes entrance bindings, serialises
     * marker positions, picks a deterministic random marker offset, validates
     * the populationSpec, saves the quest, and refreshes waypoints.
     *
     * <p>{@code placed} may be {@code null} for the surface pre-placed fallback
     * path which has only {@code Vector3i} coords and no marker scan result.
     * When null, {@code poi_mob_group_positions} and {@code poi_chest_positions}
     * are omitted: {@link com.chonbosmods.quest.POIProximitySystem} falls back
     * to the POI entrance anchor for both mob-group scatter and chest placement.
     */
    public static void finalizePlacement(QuestInstance quest, ObjectiveInstance objective,
                                         Vector3i entrance, PlacedMarkers placed,
                                         Store<EntityStore> store,
                                         Ref<EntityStore> playerRef) {
        Map<String, String> bindings = quest.getVariableBindings();
        bindings.put("poi_x", String.valueOf(entrance.getX()));
        bindings.put("poi_y", String.valueOf(entrance.getY()));
        bindings.put("poi_z", String.valueOf(entrance.getZ()));
        bindings.put("poi_center_x", String.valueOf(entrance.getX()));
        bindings.put("poi_center_z", String.valueOf(entrance.getZ()));

        if (placed != null) {
            bindings.put("poi_mob_group_positions", serializeVec3dList(placed.mobGroupSpawnsWorld()));
            bindings.put("poi_chest_positions",     serializeVec3dList(placed.chestSpawnsWorld()));
        }

        Random rng = new Random(quest.getQuestId().hashCode() + quest.getConflictCount());
        double angle = rng.nextDouble() * 2 * Math.PI;
        double dist = rng.nextDouble() * 80;
        bindings.put("marker_offset_x", String.valueOf(dist * Math.cos(angle)));
        bindings.put("marker_offset_z", String.valueOf(dist * Math.sin(angle)));

        // Validate population spec at placement time so malformed specs fail
        // fast rather than at spawn. Format:
        //   KILL_MOBS:<enemyId>:<spawnCount>:<mobIlvl>:<mobBoss>:<bossIlvlOffset>
        String popSpec = objective.getPopulationSpec();
        if (popSpec != null && !popSpec.equals("NONE")) {
            String[] parts = popSpec.split(":");
            if (parts.length != 6) {
                throw new IllegalStateException(
                    "Malformed populationSpec for quest " + quest.getQuestId()
                    + ": expected 6 colon-delimited fields, got " + parts.length
                    + " (spec='" + popSpec + "')");
            }
            try {
                Integer.parseInt(parts[2]);
                Integer.parseInt(parts[3]);
                Integer.parseInt(parts[5]);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                    "Malformed populationSpec numeric field for quest " + quest.getQuestId()
                    + " (spec='" + popSpec + "')", e);
            }
            if (!"true".equals(parts[4]) && !"false".equals(parts[4])) {
                throw new IllegalStateException(
                    "Malformed populationSpec mobBoss for quest " + quest.getQuestId()
                    + ": expected 'true' or 'false', got '" + parts[4]
                    + "' (spec='" + popSpec + "')");
            }
        }

        Nat20PlayerData pd = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (pd != null) {
            QuestStateManager sm = Natural20.getInstance().getQuestSystem().getStateManager();
            Map<String, QuestInstance> allQuests = sm.getActiveQuests(pd);
            allQuests.put(quest.getQuestId(), quest);
            sm.saveActiveQuests(pd, allQuests);

            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player != null) {
                QuestMarkerProvider.refreshMarkers(player.getPlayerRef().getUuid(), pd);
            }
        }

        LOGGER.atInfo().log("POI placed for quest %s at (%d, %d, %d)",
            quest.getQuestId(), entrance.getX(), entrance.getY(), entrance.getZ());
    }

    /**
     * Serialise a list of Vector3d positions as a semicolon-delimited list of
     * int triples: {@code "x1,y1,z1;x2,y2,z2;..."}. Uses {@code Math.floor}
     * instead of int-cast so negative coordinates align to the correct block
     * cell ({@code -10.5 → -11}, not {@code -10}).
     */
    public static String serializeVec3dList(List<Vector3d> positions) {
        if (positions == null || positions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Vector3d v : positions) {
            if (sb.length() > 0) sb.append(';');
            sb.append((int) Math.floor(v.getX()))
              .append(',').append((int) Math.floor(v.getY()))
              .append(',').append((int) Math.floor(v.getZ()));
        }
        return sb.toString();
    }
}
