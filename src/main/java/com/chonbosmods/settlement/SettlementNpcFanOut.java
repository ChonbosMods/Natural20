package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.npc.NpcSpawnRole;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Shared spawn fan-out for settlement NPCs: given a settlement type and its
 * list of {@code Nat20_Npc_Spawn} marker positions, pick how many NPCs to
 * spawn (random in {@code [MIN_NPCS, markers.size()]}) and call the
 * battle-tested {@link com.chonbosmods.npc.Nat20NpcManager#spawnSettlementNpc}
 * once per selected marker.
 *
 * <p>Role assignment cycles through the type's {@link NpcSpawnRole} list in
 * declaration order, one role per NPC : marker {@code i} gets
 * {@code roles[i % roles.size()]}. A TOWN declared as
 * {@code [Guard, Villager, Artisan]} with 5 markers yields
 * {@code Guard, Villager, Artisan, Guard, Villager}. The {@code count} field
 * on each {@link NpcSpawnRole} is metadata only (leash radius / disposition
 * still matter): it no longer weighs the cycle. Every marker's world position
 * is the NPC's spawn AND leash point (handled inside
 * {@code spawnSettlementNpc}).
 */
public final class SettlementNpcFanOut {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|SettlementNpcs");

    /** Minimum NPCs per settlement. If fewer markers exist, we warn and spawn what we have. */
    public static final int MIN_NPCS = 4;

    private SettlementNpcFanOut() {}

    /**
     * Spawn NPCs at marker positions.
     *
     * @param markers world positions of {@code Nat20_Npc_Spawn} markers from the placed settlement
     * @param seed    used for both the target-count roll and marker shuffle; pass the same seed
     *                across reconcile/respawn for determinism.
     * @return list of successfully spawned NpcRecords.
     */
    public static List<NpcRecord> spawn(
            Store<EntityStore> store, World world, SettlementType type,
            List<Vector3d> markers, String cellKey, long seed) {
        if (markers.isEmpty()) {
            LOGGER.atWarning().log("Settlement %s at %s has zero Npc_Spawn markers", type, cellKey);
            return List.of();
        }

        Random rng = new Random(seed);
        int target = pickTargetCount(markers.size(), rng, type, cellKey);

        List<Vector3d> selected = new ArrayList<>(markers);
        Collections.shuffle(selected, rng);
        selected = selected.subList(0, target);

        List<NpcSpawnRole> roleCycle = type.getNpcSpawns();
        if (roleCycle.isEmpty()) {
            LOGGER.atWarning().log("Settlement %s has no NpcSpawnRole entries; skipping fan-out", type);
            return List.of();
        }

        List<NpcRecord> spawned = new ArrayList<>(target);
        for (int i = 0; i < selected.size(); i++) {
            NpcSpawnRole role = roleCycle.get(i % roleCycle.size());
            NpcRecord rec = Natural20.getInstance().getNpcManager()
                .spawnSettlementNpc(store, world, role, selected.get(i), cellKey, seed);
            if (rec != null) spawned.add(rec);
        }

        LOGGER.atInfo().log("Settlement %s at %s: spawned %d/%d NPCs across %d markers",
            type, cellKey, spawned.size(), target, markers.size());
        return spawned;
    }

    private static int pickTargetCount(int markerCount, Random rng, SettlementType type, String cellKey) {
        if (markerCount < MIN_NPCS) {
            LOGGER.atWarning().log(
                "Settlement %s at %s has only %d Npc_Spawn markers (< minimum %d); spawning all available",
                type, cellKey, markerCount, MIN_NPCS);
            return markerCount;
        }
        // Random in [MIN_NPCS, markerCount], inclusive both ends.
        return MIN_NPCS + rng.nextInt(markerCount - MIN_NPCS + 1);
    }

}
