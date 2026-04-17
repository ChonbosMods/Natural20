package com.chonbosmods.quest;

// WAYPOINT_REFRESH: requires com.chonbosmods.waypoint.QuestMarkerProvider.refreshMarkers(UUID, Nat20PlayerData)
// (see Task 0.1 investigation). The coordinator below calls refreshMarkers internally after
// rewriting the objective, so this system does not need to invoke it directly.

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.chonbosmods.quest.poi.POIGroupSpawnCoordinator;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodic proximity check for active POI quests. Two responsibilities:
 *
 * <ol>
 *   <li>KILL_MOBS POIs: on first approach, delegate to {@link POIGroupSpawnCoordinator}
 *       which rolls direction/difficulty, rewrites the objective, and spawns the group.
 *       Once a {@link MobGroupRecord} exists, chunk reconciliation ({@code MobGroupChunkListener})
 *       owns respawn; this system is done.</li>
 *   <li>FETCH_ITEM / PEACEFUL_FETCH POIs: on first approach, place the quest chest.
 *       This path has no group-spawn equivalent.</li>
 * </ol>
 *
 * <p>The legacy per-mob state machine (PENDING/ACTIVE/DETACHED) and its bindings
 * ({@code poi_mob_uuids}, {@code poi_detached_uuids}, {@code poi_mob_state},
 * {@code poi_spawn_descriptor}) are retired: the registry-backed group path supersedes them.
 */
public class POIProximitySystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|POIProx");
    private static final double SPAWN_RADIUS = 48.0;
    private static final double SPAWN_RADIUS_SQ = SPAWN_RADIUS * SPAWN_RADIUS;

    /** Tracked online player UUIDs: populated by PlayerReadyEvent, removed by PlayerDisconnectEvent. */
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();

    private final POIGroupSpawnCoordinator coordinator;
    private final Nat20MobGroupRegistry mobGroupRegistry;

    public POIProximitySystem(POIGroupSpawnCoordinator coordinator,
                              Nat20MobGroupRegistry mobGroupRegistry) {
        this.coordinator = coordinator;
        this.mobGroupRegistry = mobGroupRegistry;
    }

    public void addPlayer(UUID uuid) {
        trackedPlayers.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        trackedPlayers.remove(uuid);
    }

    public Set<UUID> getTrackedPlayers() {
        return Collections.unmodifiableSet(trackedPlayers);
    }

    /** Called every second from a scheduled executor, dispatched to {@code world.execute()}. */
    public void tick(World world) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Store<EntityStore> store = world.getEntityStore().getStore();

        for (UUID playerUuid : trackedPlayers) {
            Ref<EntityStore> entityRef = world.getEntityRef(playerUuid);
            if (entityRef == null) continue;

            Nat20PlayerData playerData = store.getComponent(entityRef, Natural20.getPlayerDataType());
            if (playerData == null) continue;

            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) continue;
            double px = transform.getPosition().getX();
            double pz = transform.getPosition().getZ();

            Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
            boolean dirty = false;

            for (QuestInstance quest : quests.values()) {
                ObjectiveInstance currentObj = quest.getCurrentObjective();
                if (currentObj == null) continue;
                if (!currentObj.hasPoi()) continue;

                // Anchor from bindings. finalizePlacement writes these for every POI
                // placement path; for cave-void POIs, objective.poiCenter is the void
                // center (underground) while poi_x/y/z is the surface entrance.
                Vector3d anchor = resolvePoiAnchor(quest.getVariableBindings(), currentObj);

                double dx = px - anchor.getX();
                double dz = pz - anchor.getZ();
                double distSq = dx * dx + dz * dz;
                if (distSq > SPAWN_RADIUS_SQ) continue;

                switch (currentObj.getType()) {
                    case KILL_MOBS, KILL_BOSS -> dirty |= maybeFirstSpawnGroup(
                            world, playerUuid, playerData, quest, currentObj, anchor);
                    case FETCH_ITEM -> {
                        // FETCH_ITEM POIs get both a chest AND guard mobs. The mob group
                        // doesn't credit the FETCH objective (kill tracking requires
                        // KILL_MOBS type), but killing guards is still rewarding.
                        dirty |= maybePlaceQuestChest(world, quest, currentObj, anchor);
                        dirty |= maybeFirstSpawnGroup(
                                world, playerUuid, playerData, quest, currentObj, anchor);
                    }
                    case PEACEFUL_FETCH -> dirty |= maybePlaceQuestChest(
                            world, quest, currentObj, anchor);
                    default -> {} // no POI behavior for other types
                }
            }

            if (dirty) {
                questSystem.getStateManager().saveActiveQuests(playerData, quests);
            }
        }
    }

    /**
     * First-spawn trigger for a POI quest. No-op if a record already exists or the
     * objective has no usable populationSpec (e.g. PEACEFUL_FETCH).
     * Returns true iff the coordinator ran and mutated quest state (caller should save).
     */
    private boolean maybeFirstSpawnGroup(World world, UUID playerUuid, Nat20PlayerData playerData,
                                         QuestInstance quest, ObjectiveInstance objective,
                                         Vector3d anchor) {
        // Key the record per quest phase (conflictCount). A multi-phase quest with POIs
        // in more than one phase would otherwise hit the phase-0 record and short-circuit.
        int poiSlotIdx = quest.getConflictCount();
        String groupKey = MobGroupRecord.keyFor(playerUuid, quest.getQuestId(), poiSlotIdx);
        if (mobGroupRegistry.get(groupKey) != null) {
            return false; // reconciliation handles respawn; nothing to do here
        }

        // Anchor-chunk-loaded gate. If the player just teleported into the chunk, the
        // chunk may still be loading when the proximity tick fires. Spawning into an
        // unloaded chunk reports success but the entities get clobbered when the chunk
        // later loads from disk. Defer until the chunk is ready; the next tick will retry.
        int anchorChunkX = (int) Math.floor(anchor.getX() / 32.0);
        int anchorChunkZ = (int) Math.floor(anchor.getZ() / 32.0);
        long anchorChunkKey = ((long) anchorChunkX << 32) | (anchorChunkZ & 0xFFFFFFFFL);
        if (world.getChunkIfLoaded(anchorChunkKey) == null) {
            return false;
        }

        String mobRole = extractMobRole(objective);
        if (mobRole == null) {
            LOGGER.atFine().log("quest %s objective %s has no usable populationSpec; skipping first-spawn",
                    quest.getQuestId(), objective.getType());
            return false;
        }

        LOGGER.atInfo().log("First-spawn trigger: quest=%s objective=%s anchor=(%d,%d,%d) mobRole=%s",
                quest.getQuestId(), objective.getType(),
                (int) anchor.getX(), (int) anchor.getY(), (int) anchor.getZ(), mobRole);

        coordinator.firstSpawn(world, anchor, mobRole, playerUuid,
                quest.getQuestId(), poiSlotIdx, quest, objective, playerData);
        return true;
    }

    /**
     * Extract the mob role id from the objective's populationSpec. The authored binding
     * {@code mob_type} has already been resolved into the objective's targetLabel; the
     * role id (what NPCPlugin.getIndex reads) lives in the second field of populationSpec.
     */
    private static String extractMobRole(ObjectiveInstance objective) {
        String spec = objective.getPopulationSpec();
        if (spec == null || spec.isEmpty() || "NONE".equals(spec)) return null;
        String[] parts = spec.split(":");
        if (parts.length < 2) return null;
        String role = parts[1];
        return role.isEmpty() ? null : role;
    }

    /**
     * Place the quest chest for a FETCH_ITEM / PEACEFUL_FETCH POI. Idempotent via the
     * {@code poi_chest_placed} binding flag. Uses the entrance anchor (surface-level),
     * not the objective's poiCenter (which is the void center for cave-dungeon POIs).
     */
    private boolean maybePlaceQuestChest(World world, QuestInstance quest,
                                         ObjectiveInstance objective, Vector3d anchor) {
        Map<String, String> b = quest.getVariableBindings();
        if ("true".equals(b.get("poi_chest_placed"))) return false;

        String fetchItemType = b.get("fetch_item_type");
        if (fetchItemType == null) return false;

        int ax = (int) anchor.getX();
        int ay = (int) anchor.getY();
        int az = (int) anchor.getZ();
        boolean placed = QuestChestPlacer.placeQuestChest(world, ax, ay, az,
                fetchItemType, b.getOrDefault("fetch_item_label", "quest item"));
        if (!placed) return false;

        b.put("poi_chest_placed", "true");
        LOGGER.atInfo().log("Placed quest chest for %s at %d %d %d",
                quest.getQuestId(), ax, ay, az);
        return true;
    }

    /**
     * Resolve the POI anchor position. Prefers {@code bindings.poi_x/y/z} (written by
     * {@code DialogueActionRegistry.finalizePlacement} — always surface-entrance level)
     * over {@link ObjectiveInstance#getPoiCenterX()} et al. (the void center for
     * cave-dungeon POIs, which is deep underground and not where players can reach).
     */
    private static Vector3d resolvePoiAnchor(Map<String, String> bindings, ObjectiveInstance objective) {
        String bx = bindings.get("poi_x");
        String by = bindings.get("poi_y");
        String bz = bindings.get("poi_z");
        if (bx != null && by != null && bz != null) {
            try {
                return new Vector3d(Double.parseDouble(bx), Double.parseDouble(by), Double.parseDouble(bz));
            } catch (NumberFormatException ignored) {}
        }
        return new Vector3d(objective.getPoiCenterX(), objective.getPoiCenterY(), objective.getPoiCenterZ());
    }
}
