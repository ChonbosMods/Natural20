package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.mob.Nat20MobAffixManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Spawns a Nat20-style monster group: N champions + 1 boss, all sharing a
 * group-level DifficultyTier. Boss may upgrade to LEGENDARY when the group
 * rolled EPIC (config-driven chance). DUNGEON_BOSS toggled via explicit flag.
 */
public class Nat20MobGroupSpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|GroupSpawn");

    public record SpawnResult(
            DifficultyTier groupDifficulty,
            DifficultyTier bossDifficulty,
            List<Ref<EntityStore>> champions,
            Ref<EntityStore> boss) {}

    private final MobScalingConfig config;
    private final Random rng = new Random();

    public Nat20MobGroupSpawner(MobScalingConfig config) {
        this.config = config;
    }

    /**
     * Spawn a group at the given anchor. Returns null if role unknown or spawn fails.
     *
     * @param forcedDifficulty if non-null, group uses this tier (skips weighted roll).
     *                         Boss still gets legendary-upgrade chance only when group==EPIC.
     * @param isDungeonBoss    if true, the boss is tagged Tier.DUNGEON_BOSS instead of BOSS.
     *                         Deferred use case (dungeon content); expose now so callers don't need to grow later.
     */
    @Nullable
    public SpawnResult spawnGroup(World world, String mobRole, int championCount,
                                  Vector3d anchor,
                                  @Nullable DifficultyTier forcedDifficulty,
                                  boolean isDungeonBoss) {
        int clampedCount = Math.max(config.groupMinChampions(),
                Math.min(config.groupMaxChampions(), championCount));

        int roleIndex = NPCPlugin.get().getIndex(mobRole);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Unknown mob role '%s'", mobRole);
            return null;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        DifficultyTier groupDiff = (forcedDifficulty != null)
                ? forcedDifficulty
                : Natural20.getInstance().getMobScaleSystem().rollDifficultyWeighted(rng);

        DifficultyTier bossDiff = groupDiff;
        if (groupDiff == DifficultyTier.EPIC
                && rng.nextInt(100) < config.bossLegendaryChance()) {
            bossDiff = DifficultyTier.LEGENDARY;
        }

        List<Ref<EntityStore>> champs = new ArrayList<>(clampedCount);
        for (int i = 0; i < clampedCount; i++) {
            Vector3d pos = spreadAround(anchor, 3.0);
            Ref<EntityStore> ref = spawnOne(world, roleIndex, pos);
            if (ref == null) continue;
            applyFullStack(store, ref, Tier.CHAMPION, groupDiff);
            champs.add(ref);
        }

        Ref<EntityStore> bossRef = spawnOne(world, roleIndex, spreadAround(anchor, 1.5));
        if (bossRef != null) {
            Tier bossRole = isDungeonBoss ? Tier.DUNGEON_BOSS : Tier.BOSS;
            applyFullStack(store, bossRef, bossRole, bossDiff);
        }

        LOGGER.atInfo().log(
                "Spawned group role=%s champions=%d groupDiff=%s bossDiff=%s dungeonBoss=%s",
                mobRole, champs.size(), groupDiff, bossDiff, isDungeonBoss);

        return new SpawnResult(groupDiff, bossDiff, champs, bossRef);
    }

    @Nullable
    private Ref<EntityStore> spawnOne(World world, int roleIndex, Vector3d pos) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3f rotation = new Vector3f(0, (float) (rng.nextDouble() * 360), 0);
        Pair<Ref<EntityStore>, NPCEntity> result =
                NPCPlugin.get().spawnEntity(store, roleIndex, pos, rotation, null, null);
        return (result != null) ? result.first() : null;
    }

    /** Scale system tag + difficulty + affix roll + nameplate for a single mob. */
    private void applyFullStack(Store<EntityStore> store,
                                Ref<EntityStore> ref, Tier role, DifficultyTier difficulty) {
        var scaleSystem = Natural20.getInstance().getMobScaleSystem();
        scaleSystem.setTier(ref, store, role);
        scaleSystem.applyDifficulty(ref, store, store, difficulty);

        Nat20MobAffixManager affixMgr =
                Natural20.getInstance().getLootSystem().getMobAffixManager();
        affixMgr.rollAndApply(ref, store, role, difficulty);
    }

    private Vector3d spreadAround(Vector3d anchor, double radius) {
        double dx = (rng.nextDouble() - 0.5) * radius * 2;
        double dz = (rng.nextDouble() - 0.5) * radius * 2;
        return new Vector3d(anchor.getX() + dx, anchor.getY(), anchor.getZ() + dz);
    }
}
