package com.chonbosmods.quest.poi;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.mob.Nat20MobAffixManager;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.MobScalingConfig;
import com.chonbosmods.progression.Nat20MobGroupSpawner;
import com.chonbosmods.progression.Nat20MobScaleSystem;
import com.chonbosmods.progression.Tier;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls direction + difficulty + affixes once per POI group at the player's first
 * approach, persists the result, drives the initial spawn, and rewrites the quest's
 * KILL_MOBS objective in place. All state is frozen at this point: chunk-reload and
 * server-restart reconciliation replay the same roll verbatim.
 *
 * <p>See {@code docs/plans/2026-04-16-poi-quest-group-spawn-integration-design.md}.
 */
public class POIGroupSpawnCoordinator {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|POICoordinator");

    public record SpawnOutcome(MobGroupRecord record, Map<Integer, UUID> spawnedByIndex) {}

    private final Nat20MobGroupRegistry registry;
    private final Nat20MobGroupSpawner spawner;
    private final Random scaleRng = new Random();

    public POIGroupSpawnCoordinator(Nat20MobGroupRegistry registry, Nat20MobGroupSpawner spawner) {
        this.registry = registry;
        this.spawner = spawner;
    }

    /**
     * First-time spawn for a POI quest's mob group. No-op (returns the existing record)
     * if a record for {@code groupKey} already exists.
     *
     * <p>On success: writes a {@link MobGroupRecord} to the registry, spawns every slot,
     * rewrites {@code objective} per {@link PoiGroupDirection}, repopulates quest bindings,
     * rebuilds {@code quest_objective_summary}, and refreshes the waypoint marker cache.
     */
    public SpawnOutcome firstSpawn(
            World world, Vector3d anchor, String mobRole,
            UUID playerUuid, String questId, int poiSlotIdx,
            QuestInstance quest, ObjectiveInstance objective,
            Nat20PlayerData playerData) {

        String groupKey = MobGroupRecord.keyFor(playerUuid, questId, poiSlotIdx);

        MobGroupRecord existing = registry.get(groupKey);
        if (existing != null) {
            LOGGER.atFine().log("firstSpawn: record already exists for %s, no-op", groupKey);
            return new SpawnOutcome(existing, Map.of());
        }

        MobScalingConfig config = Natural20.getInstance().getScalingConfig();
        Nat20MobScaleSystem scaleSystem = Natural20.getInstance().getMobScaleSystem();
        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();
        Nat20MobAffixManager affixMgr = lootSystem.getMobAffixManager();
        Map<String, String> questBindings = quest.getVariableBindings();

        // 2. Roll championCount. group/boss difficulty may be pre-rolled at
        //    quest generation when the template sets forceBossDirection=true;
        //    in that case reuse the pre-rolled values verbatim so {boss_name}
        //    (already bound and shown in exposition) matches the actual spawn.
        int championCount = ThreadLocalRandom.current().nextInt(
                config.groupMinChampions(), config.groupMaxChampions() + 1);

        DifficultyTier groupDiff = readPreRolledTier(questBindings, "group_difficulty_prerolled");
        if (groupDiff == null) groupDiff = scaleSystem.rollDifficultyWeighted(scaleRng);

        DifficultyTier bossDiff = readPreRolledTier(questBindings, "boss_difficulty_prerolled");
        if (bossDiff == null) {
            bossDiff = groupDiff;
            if (groupDiff == DifficultyTier.EPIC
                    && ThreadLocalRandom.current().nextInt(100) < config.bossLegendaryChance()) {
                bossDiff = DifficultyTier.LEGENDARY;
            }
        }
        DifficultyTier championDiff = (groupDiff == DifficultyTier.LEGENDARY)
                ? DifficultyTier.EPIC : groupDiff;

        // 3. Direction: honor objective.forcedPoiDirection when set. Otherwise default
        //    to KILL_COUNT. The original 50/50 roll was removed because existing v2
        //    dramatic templates author {kill_count} / {enemy_type_plural} directly in
        //    their exposition and conflict text: a random promotion to KILL_BOSS
        //    would leave those templates referencing a count that no longer matches
        //    the objective, and an enemy plural against a single named boss.
        //    KILL_BOSS is opt-in via the template's forceBossDirection flag.
        PoiGroupDirection direction = objective.getForcedPoiDirection();
        if (direction == null) {
            direction = PoiGroupDirection.KILL_COUNT;
        }

        // 4-5. Pre-roll shared champion + boss affixes
        List<RolledAffix> sharedChampionAffixes =
                affixMgr.rollAffixes(Tier.CHAMPION, championDiff);
        List<RolledAffix> bossAffixes = affixMgr.rollAffixes(Tier.BOSS, bossDiff);

        // 6. Boss name: reuse pre-bound name if the quest generator already wrote one
        //    (forceBossDirection path); otherwise generate from seeded RNG so
        //    reconciliation reproduces it after server restart.
        String bossName = questBindings.get("boss_name");
        if (bossName == null || bossName.isEmpty()) {
            long nameSeed = Objects.hash(groupKey, bossDiff.name());
            bossName = lootSystem.getMobNameGenerator().generate(bossDiff, new Random(nameSeed));
            if (bossName == null || bossName.isEmpty()) {
                bossName = "Unnamed";
            }
        }

        // 7. Build + persist record skeleton (slots all !isDead, currentUuid=null).
        MobGroupRecord record = new MobGroupRecord();
        record.setGroupKey(groupKey);
        record.setOwnerPlayerUuid(playerUuid.toString());
        record.setQuestId(questId);
        record.setPoiSlotIdx(poiSlotIdx);
        record.setMobRole(mobRole);
        record.setAnchor(anchor.getX(), anchor.getY(), anchor.getZ());
        record.setSpawnGenerationId(System.currentTimeMillis());
        record.setGroupDifficulty(groupDiff);
        record.setBossDifficulty(bossDiff);
        record.setDirection(direction);
        record.setChampionCount(championCount);
        record.setBossName(bossName);
        record.setSharedChampionAffixes(sharedChampionAffixes);
        record.setBossAffixes(bossAffixes);

        List<SlotRecord> slots = new ArrayList<>(championCount + 1);
        for (int i = 0; i < championCount; i++) slots.add(new SlotRecord(i, false));
        slots.add(new SlotRecord(championCount, true));
        record.setSlots(slots);

        record.setCreatedAtMillis(System.currentTimeMillis());
        registry.put(record);

        // 8. Spawn all slots via the shared spawn path. respawnSlotInternal updates each
        //    slot.currentUuid in place, so we only need to re-flush the registry after.
        Map<Integer, UUID> spawnedByIndex = spawner.spawnFromRecord(world, record);
        registry.saveAsync();

        // 10-11. KILL_MOBS/KILL_BOSS only: rewrite objective fields + rebuild summary.
        // FETCH_ITEM quests also spawn groups (as chest guards), but the objective is
        // "retrieve the {item}" — kill count and targetLabel must stay as-authored.
        com.chonbosmods.quest.ObjectiveType objType = objective.getType();
        if (objType == com.chonbosmods.quest.ObjectiveType.KILL_MOBS
                || objType == com.chonbosmods.quest.ObjectiveType.KILL_BOSS) {
            rewriteObjective(quest, objective, record);
            rebuildObjectiveSummary(quest, objective, record);
        } else {
            // Still populate §3.5 bindings so dialogue {boss_name}/{mob_type} tokens
            // resolve if any non-KILL_MOBS quest dialogue happens to reference them.
            populateSharedBindings(quest, record);
        }

        // 12. Refresh waypoint marker cache so the new label renders (KILL_MOBS case)
        // or just re-syncs (FETCH_ITEM case).
        QuestMarkerProvider.refreshMarkers(playerUuid, playerData);

        LOGGER.atInfo().log(
                "GroupSpawn direction=%s champions=%d groupDiff=%s bossDiff=%s bossName=%s groupKey=%s",
                direction, championCount, groupDiff, bossDiff, bossName, groupKey);

        return new SpawnOutcome(record, spawnedByIndex);
    }

    private void rewriteObjective(QuestInstance quest, ObjectiveInstance objective,
                                  MobGroupRecord record) {
        Map<String, String> b = quest.getVariableBindings();
        String mobTypeSingular = b.getOrDefault("enemy_type", record.getMobRole());
        String mobTypePlural = b.getOrDefault("enemy_type_plural", mobTypeSingular);

        switch (record.getDirection()) {
            case KILL_COUNT -> {
                objective.setRequiredCount(record.getChampionCount() + 1);
                objective.setTargetLabel(mobTypeSingular);
                objective.setTargetLabelPlural(mobTypePlural);
            }
            case KILL_BOSS -> {
                objective.setRequiredCount(1);
                objective.setTargetLabel(record.getBossName());
                objective.setTargetLabelPlural(record.getBossName());
            }
        }

        populateSharedBindings(quest, record);
    }

    /** §3.5 bindings populated once, at first-spawn, for any POI quest with a group. */
    private void populateSharedBindings(QuestInstance quest, MobGroupRecord record) {
        Map<String, String> b = quest.getVariableBindings();
        String mobTypeSingular = b.getOrDefault("enemy_type", record.getMobRole());
        String mobTypePlural = b.getOrDefault("enemy_type_plural", mobTypeSingular);
        b.put("mob_type", mobTypeSingular);
        b.put("mob_type_plural", mobTypePlural);
        b.put("champion_count", String.valueOf(record.getChampionCount()));
        b.put("boss_name", record.getBossName());
        b.put("group_difficulty", record.getGroupDifficulty().namePoolKey());
    }

    private void rebuildObjectiveSummary(QuestInstance quest, ObjectiveInstance obj,
                                         MobGroupRecord record) {
        String summary = switch (record.getDirection()) {
            case KILL_COUNT -> "kill " + obj.getRequiredCount() + " " + obj.getEffectiveLabel();
            case KILL_BOSS -> "kill " + record.getBossName();
        };
        quest.getVariableBindings().put("quest_objective_summary", summary);
    }

    private static DifficultyTier readPreRolledTier(Map<String, String> bindings, String key) {
        String raw = bindings.get(key);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return DifficultyTier.valueOf(raw);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("Invalid pre-rolled tier in bindings: %s=%s", key, raw);
            return null;
        }
    }
}
