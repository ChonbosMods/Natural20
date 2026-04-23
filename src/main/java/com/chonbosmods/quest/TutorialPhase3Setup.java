package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.loot.mob.naming.Nat20MobNameGenerator;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.MobScalingConfig;
import com.chonbosmods.progression.Nat20MobScaleSystem;
import com.chonbosmods.quest.model.DifficultyConfig;
import com.chonbosmods.quest.poi.PoiGroupDirection;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.world.Nat20HeightmapSampler;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Map;
import java.util.Random;

/**
 * Phase 3 setup for the tutorial quest: pre-rolls the boss + claims a POI at
 * phase 2 turn-in. Produces the same binding surface as {@code QuestGenerator}'s
 * {@code applyBossPreRoll} and {@code resolveAndPlacePoi} so downstream
 * systems ({@code POIProximitySystem}, {@code POIGroupSpawnCoordinator},
 * {@code POIKillTrackingSystem}) pick it up unchanged.
 *
 * <p>POI selection preference: a nearby cave void (claimed via
 * {@link CaveVoidRegistry}) first, falling back to a deterministic surface
 * point ~300 blocks east of the spawn settlement when no void is near.
 */
public final class TutorialPhase3Setup {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|TutorialPhase3Setup");

    /** Surface fallback distance from Celius settlement (blocks). */
    private static final int SURFACE_FALLBACK_DX = 300;
    private static final int SURFACE_FALLBACK_DZ = 0;

    /** Preferred enemy type for the tutorial boss. */
    private static final String ENEMY_TYPE_ID = "Skeleton";
    private static final String ENEMY_TYPE = "skeleton";
    private static final String ENEMY_TYPE_PLURAL = "skeletons";

    private TutorialPhase3Setup() {}

    /**
     * Hydrate the phase-3 objective and its bindings. Safe to call on a quest
     * whose phase-3 objective is already pre-rolled: it only re-writes when
     * the objective still has targetId "deferred_boss".
     */
    public static void setupPhase3(QuestInstance quest, ObjectiveInstance phase3Obj) {
        if (phase3Obj == null) return;
        if (!"deferred_boss".equals(phase3Obj.getTargetId())
                && phase3Obj.hasPoi()) {
            return; // already set up
        }

        Map<String, String> bindings = quest.getVariableBindings();
        Natural20 plugin = Natural20.getInstance();
        MobScalingConfig scalingConfig = plugin.getScalingConfig();
        Nat20MobScaleSystem scaleSystem = plugin.getMobScaleSystem();
        Nat20MobNameGenerator nameGen = plugin.getLootSystem().getMobNameGenerator();

        // Deterministic RNG keyed on questId so reloading the quest in the same
        // session produces the same boss name / difficulty tier.
        Random rng = new Random(quest.getQuestId().hashCode() ^ 0xB055L);
        DifficultyTier groupDiff = scaleSystem.rollDifficultyWeighted(rng);
        DifficultyTier bossDiff = groupDiff;
        if (groupDiff == DifficultyTier.EPIC
                && rng.nextInt(100) < scalingConfig.bossLegendaryChance()) {
            bossDiff = DifficultyTier.LEGENDARY;
        }

        String bossName = nameGen.generate(bossDiff, rng);
        if (bossName == null || bossName.isEmpty()) bossName = "Unnamed";

        bindings.put("boss_name", bossName);
        bindings.put("group_difficulty", groupDiff.namePoolKey());
        bindings.put("group_difficulty_prerolled", groupDiff.name());
        bindings.put("boss_difficulty_prerolled", bossDiff.name());
        bindings.put("enemy_type_id", ENEMY_TYPE_ID);
        bindings.put("enemy_type", ENEMY_TYPE);
        bindings.put("enemy_type_plural", ENEMY_TYPE_PLURAL);
        bindings.put("mob_type", ENEMY_TYPE);
        bindings.put("mob_type_plural", ENEMY_TYPE_PLURAL);
        bindings.put("kill_count", "1");

        phase3Obj.setTargetId(bossName);
        phase3Obj.setTargetLabel(bossName);
        phase3Obj.setTargetLabelPlural(bossName);
        phase3Obj.setForcedPoiDirection(PoiGroupDirection.KILL_BOSS);
        phase3Obj.setRequiredCount(1);

        // ----- Build populationSpec from the tutorial quest's DifficultyConfig -----
        String populationSpec = buildPopulationSpec(quest);

        // ----- POI selection: nearby cave void, else surface fallback east of spawn -----
        int[] anchor = resolveSpawnAnchor();
        int anchorX = anchor[0], anchorZ = anchor[1];

        CaveVoidRegistry voidRegistry = plugin.getCaveVoidRegistry();
        CaveVoidRecord void_ = voidRegistry != null
            ? voidRegistry.findNearbyVoid(anchorX, anchorZ, 200, 600)
            : null;

        int poiX, poiY, poiZ;
        if (void_ != null) {
            voidRegistry.claimVoid(void_, quest.getSourceSettlementId());
            poiX = void_.getCenterX();
            poiY = void_.getCenterY();
            poiZ = void_.getCenterZ();
            LOGGER.atInfo().log("Tutorial phase-3: claimed cave void at (%d,%d,%d)", poiX, poiY, poiZ);
        } else {
            poiX = anchorX + SURFACE_FALLBACK_DX;
            poiZ = anchorZ + SURFACE_FALLBACK_DZ;
            poiY = sampleSurfaceY(poiX, poiZ, anchor[2]);
            LOGGER.atInfo().log(
                "Tutorial phase-3: no cave void near spawn, surface fallback at (%d,%d,%d)",
                poiX, poiY, poiZ);
        }

        phase3Obj.setPoi(poiX, poiY, poiZ, populationSpec);

        // Surface anchor bindings so waypoint + UI can render without dereffing cave data.
        bindings.put("poi_center_x", String.valueOf(poiX));
        bindings.put("poi_center_z", String.valueOf(poiZ));
        bindings.put("poi_available", "true");
        bindings.putIfAbsent("marker_offset_x", "0");
        bindings.putIfAbsent("marker_offset_z", "0");

        QuestGenerator.buildObjectiveSummary(phase3Obj, bindings);
        LOGGER.atInfo().log(
            "Tutorial phase-3 hydrated: boss=%s tier=%s pop=%s",
            bossName, bossDiff, populationSpec);
    }

    private static String buildPopulationSpec(QuestInstance quest) {
        String difficultyId = quest.getDifficultyId() != null
            ? quest.getDifficultyId() : TutorialQuestFactory.DIFFICULTY_ID;
        DifficultyConfig difficulty = Natural20.getInstance().getQuestSystem()
            .getDifficultyRegistry().get(difficultyId);
        if (difficulty == null) {
            throw new IllegalStateException(
                "Tutorial phase-3: missing DifficultyConfig '" + difficultyId + "'");
        }
        int spawnCount = 4;
        return "KILL_MOBS:" + ENEMY_TYPE_ID + ":" + spawnCount
            + ":" + difficulty.mobIlvl()
            + ":" + difficulty.mobBoss()
            + ":" + difficulty.bossIlvlOffset();
    }

    private static int[] resolveSpawnAnchor() {
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements != null) {
            SettlementRecord spawn = settlements.getByCell(TutorialQuestFactory.SOURCE_SETTLEMENT_ID);
            if (spawn != null) {
                return new int[]{(int) spawn.getPosX(), (int) spawn.getPosZ(), (int) spawn.getPosY()};
            }
        }
        return new int[]{0, 0, 64};
    }

    private static int sampleSurfaceY(int x, int z, int fallbackY) {
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return fallbackY > 0 ? fallbackY : 64;
        try {
            Nat20HeightmapSampler.SampleResult res = Nat20HeightmapSampler.sample(
                world, x, z, 0, 0, Nat20HeightmapSampler.Mode.MEDIAN);
            return res.y() > 0 ? res.y() : (fallbackY > 0 ? fallbackY : 64);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("sampleSurfaceY failed at (%d,%d)", x, z);
            return fallbackY > 0 ? fallbackY : 64;
        }
    }
}
