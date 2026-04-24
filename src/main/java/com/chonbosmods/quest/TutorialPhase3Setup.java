package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.loot.mob.naming.Nat20MobNameGenerator;
import com.chonbosmods.prefab.PlacedMarkers;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.MobScalingConfig;
import com.chonbosmods.progression.Nat20MobScaleSystem;
import com.chonbosmods.quest.model.DifficultyConfig;
import com.chonbosmods.quest.poi.PoiGroupDirection;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.world.Nat20HeightmapSampler;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Phase 3 setup for the tutorial quest. Pre-rolls the boss (synchronous), then
 * kicks off async POI placement: a nearby cave void is claimed and a hostile-POI
 * dungeon prefab is pasted into it so the mob group spawns in a carved-out room
 * rather than inside solid rock. Falls back to a surface anchor when no void is
 * near. Produces the same binding surface as {@code QuestGenerator}'s
 * {@code applyBossPreRoll} + {@code resolveAndPlacePoi} so downstream systems
 * (POIProximitySystem, POIGroupSpawnCoordinator, POIKillTrackingSystem) pick
 * it up unchanged.
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
     * Run phase-3 setup: boss pre-roll + async POI placement. Safe to call on a
     * quest whose phase-3 objective is already set up (re-entry is a no-op).
     */
    public static void setupPhase3(QuestInstance quest, ObjectiveInstance phase3Obj,
                                   World world, Store<EntityStore> store) {
        if (phase3Obj == null) return;
        if (!"deferred_boss".equals(phase3Obj.getTargetId())
                && phase3Obj.hasPoi()) {
            return;
        }

        preRollBoss(quest, phase3Obj);
        String populationSpec = buildPopulationSpec(quest);
        placePoi(quest, phase3Obj, populationSpec, world, store);
    }

    private static void preRollBoss(QuestInstance quest, ObjectiveInstance phase3Obj) {
        Map<String, String> bindings = quest.getVariableBindings();
        Natural20 plugin = Natural20.getInstance();
        MobScalingConfig scalingConfig = plugin.getScalingConfig();
        Nat20MobScaleSystem scaleSystem = plugin.getMobScaleSystem();
        Nat20MobNameGenerator nameGen = plugin.getLootSystem().getMobNameGenerator();

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

        LOGGER.atInfo().log("Tutorial phase-3 pre-roll: boss=%s tier=%s", bossName, bossDiff);
    }

    private static void placePoi(QuestInstance quest, ObjectiveInstance phase3Obj,
                                 String populationSpec, World world, Store<EntityStore> store) {
        Natural20 plugin = Natural20.getInstance();
        int[] anchor = resolveSpawnAnchor(quest);
        int anchorX = anchor[0], anchorZ = anchor[1];

        CaveVoidRegistry voidRegistry = plugin.getCaveVoidRegistry();
        CaveVoidRecord void_ = voidRegistry != null
            ? voidRegistry.findNearbyVoid(anchorX, anchorZ, 200, 600)
            : null;

        if (void_ == null || voidRegistry == null) {
            applySurfaceFallback(quest, phase3Obj, populationSpec, anchor);
            return;
        }

        voidRegistry.claimVoid(void_, quest.getSourceSettlementId());
        LOGGER.atInfo().log("Tutorial phase-3: claimed cave void at (%d,%d,%d)",
            void_.getCenterX(), void_.getCenterY(), void_.getCenterZ());

        // Paste the dungeon prefab so the mob group has a carved-out room to
        // spawn in. placeAtVoid is async; on completion we rebind the POI to
        // the dungeon's entrance and write the mob-group / chest marker
        // positions so POIGroupSpawnCoordinator scatters at real floor tiles
        // rather than inside stone walls.
        plugin.getStructurePlacer()
            .placeAtVoid(world, void_, store)
            .whenComplete((placed, error) -> world.execute(() -> {
                if (error != null || placed == null) {
                    if (error != null) {
                        LOGGER.atWarning().withCause(error).log(
                            "Tutorial phase-3: dungeon placement failed; surface fallback");
                    } else {
                        LOGGER.atWarning().log(
                            "Tutorial phase-3: dungeon placement returned null; surface fallback");
                    }
                    applySurfaceFallback(quest, phase3Obj, populationSpec, anchor);
                    return;
                }

                Vector3i entrance = placed.anchorWorld();
                phase3Obj.setPoi(entrance.getX(), entrance.getY(), entrance.getZ(), populationSpec);

                Map<String, String> bindings = quest.getVariableBindings();
                bindings.put("poi_x", String.valueOf(entrance.getX()));
                bindings.put("poi_y", String.valueOf(entrance.getY()));
                bindings.put("poi_z", String.valueOf(entrance.getZ()));
                bindings.put("poi_center_x", String.valueOf(entrance.getX()));
                bindings.put("poi_center_z", String.valueOf(entrance.getZ()));
                bindings.put("poi_mob_group_positions",
                    serializeVec3dList(placed.mobGroupSpawnsWorld()));
                bindings.put("poi_chest_positions",
                    serializeVec3dList(placed.chestSpawnsWorld()));
                bindings.put("poi_available", "true");
                bindings.putIfAbsent("marker_offset_x", "0");
                bindings.putIfAbsent("marker_offset_z", "0");

                QuestGenerator.buildObjectiveSummary(phase3Obj, bindings);
                LOGGER.atInfo().log(
                    "Tutorial phase-3 dungeon pasted; POI anchored at (%d,%d,%d) with %d mob markers",
                    entrance.getX(), entrance.getY(), entrance.getZ(),
                    placed.mobGroupSpawnsWorld().size());
            }));
    }

    private static void applySurfaceFallback(QuestInstance quest, ObjectiveInstance phase3Obj,
                                             String populationSpec, int[] anchor) {
        int poiX = anchor[0] + SURFACE_FALLBACK_DX;
        int poiZ = anchor[1] + SURFACE_FALLBACK_DZ;
        int poiY = sampleSurfaceY(poiX, poiZ, anchor[2]);
        phase3Obj.setPoi(poiX, poiY, poiZ, populationSpec);

        Map<String, String> bindings = quest.getVariableBindings();
        bindings.put("poi_center_x", String.valueOf(poiX));
        bindings.put("poi_center_z", String.valueOf(poiZ));
        bindings.put("poi_available", "true");
        bindings.putIfAbsent("marker_offset_x", "0");
        bindings.putIfAbsent("marker_offset_z", "0");

        QuestGenerator.buildObjectiveSummary(phase3Obj, bindings);
        LOGGER.atInfo().log("Tutorial phase-3 surface fallback at (%d,%d,%d) (no dungeon prefab)",
            poiX, poiY, poiZ);
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

    private static int[] resolveSpawnAnchor(QuestInstance quest) {
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        String cellKey = quest != null ? quest.getSourceSettlementId() : null;
        if (settlements != null && cellKey != null) {
            SettlementRecord spawn = settlements.getByCell(cellKey);
            if (spawn != null) {
                return new int[]{(int) spawn.getPosX(), (int) spawn.getPosZ(), (int) spawn.getPosY()};
            }
        }
        if (settlements != null) {
            SettlementRecord celius = TutorialQuestFactory.findCeliusSettlement(settlements);
            if (celius != null) {
                return new int[]{(int) celius.getPosX(), (int) celius.getPosZ(), (int) celius.getPosY()};
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

    private static String serializeVec3dList(List<Vector3d> positions) {
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
