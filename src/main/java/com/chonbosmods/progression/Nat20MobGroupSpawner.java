package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.mob.Nat20MobAffixManager;
import com.chonbosmods.loot.mob.Nat20MobGroupMemberComponent;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.SlotRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

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

        // Champions cap at EPIC. If the group itself is LEGENDARY (only possible via forced),
        // champions drop to EPIC while the boss retains LEGENDARY.
        DifficultyTier championDiff = (groupDiff == DifficultyTier.LEGENDARY)
                ? DifficultyTier.EPIC : groupDiff;

        // All champions in a group share one affix set (the group's "champion roll");
        // the boss rolls independently. Fewer rolls + visually-coherent minion packs.
        Nat20MobAffixManager affixMgr =
                Natural20.getInstance().getLootSystem().getMobAffixManager();
        List<RolledAffix> sharedChampionAffixes =
                affixMgr.rollAffixes(Tier.CHAMPION, championDiff);

        List<Ref<EntityStore>> champs = new ArrayList<>(clampedCount);
        for (int i = 0; i < clampedCount; i++) {
            Vector3d pos = spreadAround(anchor, 3.0);
            Ref<EntityStore> ref = spawnOne(world, roleIndex, pos);
            if (ref == null) continue;
            applySharedChampionStack(store, ref, championDiff, sharedChampionAffixes, affixMgr);
            champs.add(ref);
        }

        Ref<EntityStore> bossRef = spawnOne(world, roleIndex, spreadAround(anchor, 1.5));
        if (bossRef != null) {
            Tier bossRole = isDungeonBoss ? Tier.DUNGEON_BOSS : Tier.BOSS;
            applyFullStack(store, bossRef, bossRole, bossDiff);
        }

        LOGGER.atInfo().log(
                "Spawned group role=%s champions=%d groupDiff=%s bossDiff=%s dungeonBoss=%s sharedChampionAffixes=%s",
                mobRole, champs.size(), groupDiff, bossDiff, isDungeonBoss,
                sharedChampionAffixes.stream().map(RolledAffix::id).toList());

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

    /** Scale system tag + difficulty + fresh affix roll (boss path). */
    private void applyFullStack(Store<EntityStore> store,
                                Ref<EntityStore> ref, Tier role, DifficultyTier difficulty) {
        var scaleSystem = Natural20.getInstance().getMobScaleSystem();
        scaleSystem.setTier(ref, store, role);
        scaleSystem.applyDifficulty(ref, store, store, difficulty);

        Nat20MobAffixManager affixMgr =
                Natural20.getInstance().getLootSystem().getMobAffixManager();
        affixMgr.rollAndApply(ref, store, role, difficulty);
    }

    /** Scale system tag + difficulty + shared pre-rolled affix set (champion group path). */
    private void applySharedChampionStack(Store<EntityStore> store, Ref<EntityStore> ref,
                                          DifficultyTier difficulty,
                                          List<RolledAffix> sharedAffixes,
                                          Nat20MobAffixManager affixMgr) {
        var scaleSystem = Natural20.getInstance().getMobScaleSystem();
        scaleSystem.setTier(ref, store, Tier.CHAMPION);
        scaleSystem.applyDifficulty(ref, store, store, difficulty);
        affixMgr.applyAffixes(ref, store, Tier.CHAMPION, difficulty, sharedAffixes);
    }

    private Vector3d spreadAround(Vector3d anchor, double radius) {
        double dx = (rng.nextDouble() - 0.5) * radius * 2;
        double dz = (rng.nextDouble() - 0.5) * radius * 2;
        return new Vector3d(anchor.getX() + dx, anchor.getY(), anchor.getZ() + dz);
    }

    // ── Record-driven spawn paths (persistent group registry + reconciliation) ────

    /**
     * Spawn every {@code !isDead} slot in the record that does not already have a live entity.
     * Applies stored tier, difficulty, affixes, and nameplate from the record: no re-rolls.
     * Attaches {@link Nat20MobGroupMemberComponent} to each spawn so reconciliation can
     * match by {@code (groupKey, slotIndex)} instead of UUID.
     *
     * @return map of {@code slotIndex -> spawned UUID} for every slot successfully spawned.
     */
    public Map<Integer, UUID> spawnFromRecord(World world, MobGroupRecord record) {
        Map<Integer, UUID> out = new HashMap<>();
        for (SlotRecord slot : record.getSlots()) {
            if (slot.isDead()) continue;
            if (slot.getCurrentUuid() != null) continue;
            UUID uuid = respawnSlotInternal(world, record, slot);
            if (uuid != null) out.put(slot.getSlotIndex(), uuid);
        }
        return out;
    }

    /**
     * Spawn a single slot from its record state. Used by {@code MobGroupChunkListener} when
     * reconciliation finds an individual slot missing on chunk load. Does NOT roll anything.
     *
     * @return the spawned UUID, or null if spawn failed.
     */
    public @Nullable UUID respawnSlot(World world, MobGroupRecord record, SlotRecord slot) {
        if (slot.isDead()) return null;
        return respawnSlotInternal(world, record, slot);
    }

    private @Nullable UUID respawnSlotInternal(World world, MobGroupRecord record, SlotRecord slot) {
        int roleIndex = NPCPlugin.get().getIndex(record.getMobRole());
        if (roleIndex < 0) {
            LOGGER.atWarning().log("respawnSlot: unknown role '%s' for groupKey=%s",
                    record.getMobRole(), record.getGroupKey());
            return null;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3d anchor = new Vector3d(record.getAnchorX(), record.getAnchorY(), record.getAnchorZ());
        Vector3d pos = spreadAround(anchor, slot.isBoss() ? 1.5 : 3.0);
        Vector3f rotation = new Vector3f(0, (float) (rng.nextDouble() * 360), 0);

        Pair<Ref<EntityStore>, NPCEntity> result =
                NPCPlugin.get().spawnEntity(store, roleIndex, pos, rotation, null, null);
        if (result == null) return null;

        Ref<EntityStore> ref = result.first();
        UUID uuid = result.second().getUuid();

        // Stable identity: member component written before anything else so reconciliation
        // sees it even if a later apply throws.
        try {
            store.putComponent(ref, Natural20.getMobGroupMemberType(),
                    new Nat20MobGroupMemberComponent(record.getGroupKey(), slot.getSlotIndex()));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to attach member component to slot %d of %s",
                    slot.getSlotIndex(), record.getGroupKey());
        }

        var scaleSystem = Natural20.getInstance().getMobScaleSystem();
        Nat20MobAffixManager affixMgr =
                Natural20.getInstance().getLootSystem().getMobAffixManager();

        if (slot.isBoss()) {
            scaleSystem.setTier(ref, store, Tier.BOSS);
            scaleSystem.applyDifficulty(ref, store, store, record.getBossDifficulty());
            affixMgr.applyAffixes(ref, store, Tier.BOSS, record.getBossDifficulty(), record.getBossAffixes());
            // Overwrite the fresh nameplate applyAffixes generated with the stored deterministic name.
            String bossName = record.getBossName();
            if (bossName != null && !bossName.isEmpty()) {
                try {
                    store.putComponent(ref, Nameplate.getComponentType(), new Nameplate(bossName));
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e)
                            .log("Failed to apply stored boss nameplate for %s", record.getGroupKey());
                }
            }
        } else {
            DifficultyTier championDiff = (record.getGroupDifficulty() == DifficultyTier.LEGENDARY)
                    ? DifficultyTier.EPIC : record.getGroupDifficulty();
            scaleSystem.setTier(ref, store, Tier.CHAMPION);
            scaleSystem.applyDifficulty(ref, store, store, championDiff);
            affixMgr.applyAffixes(ref, store, Tier.CHAMPION, championDiff,
                    record.getSharedChampionAffixes());
        }

        slot.setCurrentUuid(uuid.toString());
        return uuid;
    }
}
