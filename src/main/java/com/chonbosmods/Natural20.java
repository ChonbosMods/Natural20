package com.chonbosmods;

import com.chonbosmods.combat.CombatDebugSystem;
import com.chonbosmods.combat.Nat20AbsorptionSystem;
import com.chonbosmods.combat.Nat20AttackSpeedSystem;
import com.chonbosmods.combat.Nat20CombatParticleSystem;
import com.chonbosmods.combat.Nat20CritSystem;
import com.chonbosmods.combat.Nat20DeepWoundsSystem;
import com.chonbosmods.combat.Nat20DotTickSystem;
import com.chonbosmods.combat.Nat20ElementalDamageSystem;
import com.chonbosmods.combat.Nat20ElementalDotSystem;
import com.chonbosmods.combat.Nat20BackstabSystem;
import com.chonbosmods.combat.Nat20PrecisionSystem;
import com.chonbosmods.mining.Nat20HasteSystem;
import com.chonbosmods.mining.Nat20ShapeMiningSystem;
import com.chonbosmods.mining.Nat20TelekinesisSystem;
import com.chonbosmods.combat.Nat20BlockProficiencySystem;
import com.chonbosmods.combat.Nat20CrushingBlowSystem;
import com.chonbosmods.combat.Nat20EvasionSystem;
import com.chonbosmods.combat.Nat20RallyAmplifySystem;
import com.chonbosmods.combat.Nat20RallySystem;
import com.chonbosmods.combat.Nat20ResilienceSystem;
import com.chonbosmods.combat.Nat20LightFootSystem;
import com.chonbosmods.combat.Nat20WaterBreathingSystem;
import com.chonbosmods.combat.Nat20ThornsSystem;
import com.chonbosmods.combat.Nat20GallantReduceSystem;
import com.chonbosmods.combat.Nat20GallantSystem;
import com.chonbosmods.combat.Nat20HexConsumeSystem;
import com.chonbosmods.combat.Nat20HexSystem;
import com.chonbosmods.combat.Nat20LifeLeechSystem;
import com.chonbosmods.combat.Nat20ManaLeechSystem;
import com.chonbosmods.combat.Nat20ViciousMockeryAmplifySystem;
import com.chonbosmods.combat.Nat20ViciousMockerySystem;
import com.chonbosmods.combat.Nat20WeaknessAmplifySystem;
import com.chonbosmods.combat.Nat20WeaknessApplySystem;
import com.chonbosmods.combat.Nat20ResistanceSystem;
import com.chonbosmods.combat.Nat20FocusedMindSystem;
import com.chonbosmods.combat.Nat20MobDmgScaleSystem;
import com.chonbosmods.combat.Nat20MovementSpeedSystem;
import com.chonbosmods.combat.Nat20ScoreBonusSystem;
import com.chonbosmods.combat.Nat20ScoreDamageSystem;
import com.chonbosmods.combat.Nat20ScoreDirtyFlag;
import com.chonbosmods.combat.Nat20ScoreRegenSystem;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.cave.CaveVoidScanner;
import com.chonbosmods.cave.UndergroundStructurePlacer;
import com.chonbosmods.commands.Nat20Command;
import com.chonbosmods.data.Nat20GlobalData;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.progression.Nat20ContributorTrackingSystem;
import com.chonbosmods.progression.Nat20DamageContributorTracker;
import com.chonbosmods.progression.Nat20HostilePool;
import com.chonbosmods.progression.Nat20MobGroupSpawner;
import com.chonbosmods.progression.Nat20MobThemeRegistry;
import com.chonbosmods.progression.Nat20SpeciesXpRegistry;
import com.chonbosmods.loot.mob.Nat20MobAffixes;
import com.chonbosmods.loot.mob.Nat20MobGroupMemberComponent;
import com.chonbosmods.loot.mob.Nat20MobLootDropSystem;
import com.chonbosmods.progression.Nat20MobLevel;
import com.chonbosmods.progression.Nat20MobGroupCombatStampSystem;
import com.chonbosmods.progression.Nat20MobGroupDedupSystem;
import com.chonbosmods.progression.Nat20MobGroupLeashSystem;
import com.chonbosmods.progression.Nat20MobScaleSystem;
import com.chonbosmods.progression.Nat20MobTintTickSystem;
import com.chonbosmods.progression.MobScalingConfig;
import com.chonbosmods.progression.Nat20XpOnKillSystem;
import com.chonbosmods.progression.Nat20XpService;
import com.chonbosmods.progression.PlayerLevelHpSystem;
import com.chonbosmods.progression.ambient.AmbientAnchorFinder;
import com.chonbosmods.progression.ambient.AmbientSpawnConfig;
import com.chonbosmods.progression.ambient.AmbientSpawnSystem;
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.dialogue.DialogueLoader;
import com.chonbosmods.dialogue.DialogueManager;
import com.chonbosmods.loot.Nat20EquipmentListener;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.chest.Nat20ChestAffixInjectionSystem;
import com.chonbosmods.loot.chest.Nat20ChestLootConfig;
import com.chonbosmods.loot.chest.Nat20ChestLootPicker;
import com.chonbosmods.loot.chest.Nat20ChestLootRoller;
import com.chonbosmods.loot.chest.Nat20ChestRollRegistry;
import com.chonbosmods.quest.CollectResourceTrackingSystem;
import com.chonbosmods.quest.FetchItemTrackingSystem;
import com.chonbosmods.quest.POIKillTrackingSystem;
import com.chonbosmods.quest.POIPopulationListener;
import com.chonbosmods.quest.POIProximitySystem;
import com.chonbosmods.quest.QuestChestPlacer;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestStateManager;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import com.chonbosmods.party.Nat20PartyInviteRegistry;
import com.chonbosmods.party.Nat20PartyRegistry;
import com.chonbosmods.npc.BuilderActionNat20StartDialogue;
import com.chonbosmods.npc.Nat20NpcManager;
import com.chonbosmods.prefab.Nat20PrefabConstants;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementNpcDeathSystem;
import com.chonbosmods.settlement.SettlementPlacer;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementDiscoverySystem;
import com.chonbosmods.quest.poi.MobGroupChunkListener;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.chonbosmods.quest.poi.POIGroupSpawnCoordinator;
import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.settlement.SettlementThreatSystem;
import com.chonbosmods.settlement.SettlementWorldGenListener;
import com.chonbosmods.ui.CharacterSheetManager;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.meta.state.BlockMapMarkersResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Natural20 extends JavaPlugin {

    private static Natural20 instance;

    private static ComponentType<EntityStore, Nat20NpcData> npcDataType;
    private static ComponentType<EntityStore, Nat20PlayerData> playerDataType;
    private static ComponentType<EntityStore, Nat20MobLevel> mobLevelType;
    private static ComponentType<EntityStore, Nat20MobAffixes> mobAffixesType;
    private static ComponentType<EntityStore, Nat20MobGroupMemberComponent> mobGroupMemberType;

    private final SettlementPlacer placer = new SettlementPlacer();
    private final Nat20NpcManager npcManager = new Nat20NpcManager();
    private final DialogueActionRegistry actionRegistry = new DialogueActionRegistry();
    private final DialogueLoader dialogueLoader = new DialogueLoader();
    private final DialogueManager dialogueManager = new DialogueManager(dialogueLoader, actionRegistry);
    private final Nat20LootSystem lootSystem = new Nat20LootSystem();
    private QuestSystem questSystem;
    private Nat20PartyQuestStore partyQuestStore;
    private Nat20PartyRegistry partyRegistry;
    private Nat20PartyInviteRegistry partyInviteRegistry;
    private final Nat20EquipmentListener equipmentListener = new Nat20EquipmentListener(lootSystem);
    private SettlementRegistry settlementRegistry;
    private Nat20MobGroupRegistry mobGroupRegistry;
    private Nat20ChestRollRegistry chestRollRegistry;
    private final Nat20HostilePool hostilePool = new Nat20HostilePool();
    private final com.chonbosmods.world.Nat20ZoneRegistry zoneRegistry = new com.chonbosmods.world.Nat20ZoneRegistry();
    private final Nat20MobThemeRegistry mobThemeRegistry = new Nat20MobThemeRegistry();
    private final Nat20SpeciesXpRegistry speciesXpRegistry = new Nat20SpeciesXpRegistry();
    private POIGroupSpawnCoordinator poiGroupSpawnCoordinator;
    private CaveVoidRegistry caveVoidRegistry;
    private CaveVoidScanner caveVoidScanner;
    private UndergroundStructurePlacer structurePlacer;
    private POIPopulationListener poiPopulationListener;
    private POIProximitySystem poiProximitySystem;
    private SettlementDiscoverySystem settlementDiscoverySystem;
    private java.util.concurrent.ScheduledExecutorService poiProximityExecutor;
    private Config<Nat20GlobalData> globalConfig;
    private java.util.concurrent.ScheduledExecutorService npcSyncExecutor;
    private Nat20AbsorptionSystem absorptionSystem;
    private Nat20AttackSpeedSystem attackSpeedSystem;
    private Nat20DeepWoundsSystem deepWoundsSystem;
    private Nat20FocusedMindSystem focusedMindSystem;
    private Nat20ScoreBonusSystem scoreBonusSystem;
    private Nat20ScoreRegenSystem scoreRegenSystem;
    private Nat20ScoreDamageSystem scoreDamageSystem;
    private Nat20CritSystem critSystem;
    private Nat20MovementSpeedSystem movementSpeedSystem;
    private Nat20ElementalDamageSystem elementalDamageSystem;
    private Nat20DotTickSystem dotTickSystem;
    private Nat20ElementalDotSystem elementalDotSystem;
    private Nat20LifeLeechSystem lifeLeechSystem;
    private Nat20ManaLeechSystem manaLeechSystem;
    private Nat20ViciousMockerySystem viciousMockerySystem;
    private Nat20HexSystem hexSystem;
    private Nat20GallantSystem gallantSystem;
    private Nat20WeaknessApplySystem weaknessApplySystem;
    private Nat20MobScaleSystem mobScaleSystem;
    private Nat20MobGroupSpawner mobGroupSpawner;
    private PlayerLevelHpSystem playerLevelHpSystem;
    private Nat20XpService xpService;
    private MobScalingConfig scalingConfig;
    private AmbientSpawnConfig ambientSpawnConfig;
    private AmbientSpawnSystem ambientSpawnSystem;
    private final Nat20DamageContributorTracker contributorTracker = new Nat20DamageContributorTracker();

    public Natural20(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        globalConfig = withConfig("nat20_global", Nat20GlobalData.CODEC);
    }

    public static Natural20 getInstance() {
        return instance;
    }

    public SettlementPlacer getPlacer() {
        return placer;
    }

    public Nat20NpcManager getNpcManager() {
        return npcManager;
    }

    public Config<Nat20GlobalData> getGlobalConfig() {
        return globalConfig;
    }

    public DialogueLoader getDialogueLoader() {
        return dialogueLoader;
    }

    public DialogueManager getDialogueManager() {
        return dialogueManager;
    }

    public Nat20LootSystem getLootSystem() {
        return lootSystem;
    }

    public QuestSystem getQuestSystem() {
        return questSystem;
    }

    public Nat20PartyQuestStore getPartyQuestStore() {
        return partyQuestStore;
    }

    public Nat20PartyRegistry getPartyRegistry() {
        return partyRegistry;
    }

    public Nat20PartyInviteRegistry getPartyInviteRegistry() {
        return partyInviteRegistry;
    }

    public SettlementRegistry getSettlementRegistry() {
        return settlementRegistry;
    }

    public Nat20MobGroupRegistry getMobGroupRegistry() {
        return mobGroupRegistry;
    }

    public Nat20ChestRollRegistry getChestRollRegistry() {
        return chestRollRegistry;
    }

    public Nat20HostilePool getHostilePool() {
        return hostilePool;
    }

    public com.chonbosmods.world.Nat20ZoneRegistry getZoneRegistry() {
        return zoneRegistry;
    }

    public Nat20MobThemeRegistry getMobThemeRegistry() {
        return mobThemeRegistry;
    }

    public Nat20SpeciesXpRegistry getSpeciesXpRegistry() {
        return speciesXpRegistry;
    }

    public POIGroupSpawnCoordinator getPOIGroupSpawnCoordinator() {
        return poiGroupSpawnCoordinator;
    }

    public CaveVoidRegistry getCaveVoidRegistry() { return caveVoidRegistry; }

    public CaveVoidScanner getCaveVoidScanner() { return caveVoidScanner; }

    public UndergroundStructurePlacer getStructurePlacer() { return structurePlacer; }

    public POIPopulationListener getPOIPopulationListener() { return poiPopulationListener; }

    public POIProximitySystem getPOIProximitySystem() { return poiProximitySystem; }

    private volatile World defaultWorld;
    private volatile boolean worldRegistriesInitialized = false;

    public World getDefaultWorld() {
        return defaultWorld;
    }

    /**
     * Rebind the per-world registries (mob groups, cave voids, nat20 items) to paths
     * under the loaded world's save directory and load their state. Called once from
     * the first chunk-load event, before any listener accesses registry data. Wiping
     * {@code devserver/universe/worlds/default} now also wipes these registries, which
     * prevents cross-world data bleed (e.g., mob groups from an old world spawning in
     * a freshly-generated world).
     */
    private void initWorldScopedRegistries(World world) {
        if (worldRegistriesInitialized) return;
        worldRegistriesInitialized = true;
        try {
            Path worldDataDir = world.getSavePath().resolve("nat20");
            java.nio.file.Files.createDirectories(worldDataDir);

            mobGroupRegistry.setSaveDirectory(worldDataDir);
            mobGroupRegistry.load();

            chestRollRegistry.setSaveDirectory(worldDataDir);
            chestRollRegistry.load();

            caveVoidRegistry.setSaveFile(worldDataDir.resolve("cave_voids.json"));
            caveVoidRegistry.load();

            settlementRegistry.setSaveDirectory(worldDataDir);
            settlementRegistry.load();

            // Regenerate dialogue topic graphs for settlements that persisted
            // from a prior session. Without this, any NPC whose settlement was
            // placed in a previous run falls back to the "..." graph because
            // onSettlementCreated only fires for NEW settlements placed during
            // this run's worldgen. Safe to run before questSystem is ready
            // only because setup() constructs questSystem before this method
            // is reachable (first-chunk-load is strictly later).
            regenerateAllSettlementTopics();

            // Party + quest-accepters + pending-invites state is world-scoped,
            // not server-global: parties track which players are adventuring
            // together in THIS world, and quest state belongs to the world's
            // world progression. Fresh world = fresh parties, by design.
            partyQuestStore.setSaveDirectory(worldDataDir);
            partyQuestStore.load();
            partyRegistry.setSaveDirectory(worldDataDir);
            partyRegistry.load();
            partyInviteRegistry.setSaveDirectory(worldDataDir);
            partyInviteRegistry.load();

            lootSystem.getItemRegistry().init(worldDataDir);
            lootSystem.getItemRegistry().rehydrateAll();

            getLogger().atInfo().log("World-scoped registries initialized under %s", worldDataDir);
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("Failed to init world-scoped registries");
        }
    }

    /**
     * Sync NPC nameplate colors based on quest state for all NPCs in a settlement.
     * Dispatches to the world thread since store access is required.
     */
    public void updateSettlementNameplates(SettlementRecord settlement, World world) {
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            for (NpcRecord npc : settlement.getNpcs()) {
                if (npc.getEntityUUID() == null) continue;
                Ref<EntityStore> npcRef = world.getEntityRef(npc.getEntityUUID());
                if (npcRef == null) continue;
                Nat20NpcData npcData = store.getComponent(npcRef, getNpcDataType());
                if (npcData == null) continue;
                // Sync marker from persisted NpcRecord state
                QuestMarkerManager.INSTANCE.syncFromRecord(npc.getEntityUUID(), npc);
                String persisted = npc.getMarkerState();
                if ("QUEST_TURN_IN".equals(persisted)) {
                    npcData.setQuestMarkerState(Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
                } else if (npc.getPreGeneratedQuest() != null) {
                    npcData.setQuestMarkerState(Nat20NpcData.QuestMarkerState.QUEST_AVAILABLE);
                } else {
                    npcData.setQuestMarkerState(Nat20NpcData.QuestMarkerState.NONE);
                }
            }
        });
    }

    /**
     * Iterate every settlement in {@link #settlementRegistry} and regenerate
     * its dialogue topic graphs, registering the results into
     * {@link #dialogueLoader}. No-op if {@code questSystem} is still null.
     *
     * <p>Must be called after {@code settlementRegistry.load()} so that
     * settlements from prior sessions pick up dialogue graphs. Previously this
     * loop only ran in {@link #setup()} before world-scoped registries
     * loaded, leaving every persisted settlement's NPCs speaking only the
     * "..." fallback graph.
     *
     * <p>Iteration is chronological by {@code placedAt} (tiebreak on cellKey)
     * so the shared per-world dedup evolves deterministically, matching how
     * it evolved during the original worldgen pass.
     */
    private void regenerateAllSettlementTopics() {
        if (questSystem == null) return;
        List<SettlementRecord> orderedSettlements = new ArrayList<>(
            settlementRegistry.getAll().values());
        if (orderedSettlements.isEmpty()) {
            getLogger().atInfo().log("Generated procedural topics for 0 settlement(s)");
            return;
        }
        orderedSettlements.sort(
            java.util.Comparator.comparingLong(SettlementRecord::getPlacedAt)
                .thenComparing(SettlementRecord::getCellKey));
        java.util.Set<UUID> resetWorlds = new java.util.HashSet<>();
        for (SettlementRecord settlement : orderedSettlements) {
            UUID worldId = settlement.getWorldUUID();
            if (worldId != null && resetWorlds.add(worldId)) {
                questSystem.getTopicGenerator().resetDedupForWorld(worldId);
            }
            var topicGraphs = questSystem.getTopicGenerator()
                .generate(settlement, deriveNearbyNames(settlement));
            dialogueLoader.registerGeneratedGraphs(topicGraphs);
        }
        getLogger().atInfo().log("Generated procedural topics for %d settlement(s)",
            orderedSettlements.size());
    }

    /**
     * Called when a new settlement is created during world generation.
     * Generates procedural topic dialogue graphs and scans for nearby cave voids.
     */
    public void onSettlementCreated(SettlementRecord settlement, World world) {
        // Scan for cave voids BEFORE quest generation so POI objectives can claim them
        if (caveVoidScanner != null && world != null) {
            scanVoidsAroundSettlement(settlement, world);
        }

        // Pre-place surface fallback POI prefabs near the settlement
        if (world != null) {
            placeSurfaceFallbackPois(settlement, world);
        }

        if (questSystem != null) {
            var topicGraphs = questSystem.getTopicGenerator().generate(settlement, deriveNearbyNames(settlement));
            dialogueLoader.registerGeneratedGraphs(topicGraphs);
        }

        // Update NPC nameplates AFTER quest generation completes
        updateSettlementNameplates(settlement, world);
    }

    /**
     * Synchronously scan for cave voids in the 200-600 block range around a settlement.
     * Runs all at once (not batched) so voids are available for quest generation.
     * Stops early once maxVoids are found.
     */
    private void scanVoidsAroundSettlement(SettlementRecord settlement, World world) {
        int centerX = (int) settlement.getPosX();
        int centerZ = (int) settlement.getPosZ();
        int scanRadius = 600;
        int chunkRadius = scanRadius / 32;
        int centerChunkX = Math.floorDiv(centerX, 32);
        int centerChunkZ = Math.floorDiv(centerZ, 32);
        int startCX = centerChunkX - chunkRadius;
        int endCX = centerChunkX + chunkRadius;
        int startCZ = centerChunkZ - chunkRadius;
        int endCZ = centerChunkZ + chunkRadius;
        int beforeCount = caveVoidRegistry.getCount();
        int maxVoids = 8;

        for (int cx = startCX; cx <= endCX; cx++) {
            for (int cz = startCZ; cz <= endCZ; cz++) {
                caveVoidScanner.scanChunk(world, cx * 32, cz * 32);
                if (caveVoidRegistry.getCount() - beforeCount >= maxVoids) break;
            }
            if (caveVoidRegistry.getCount() - beforeCount >= maxVoids) break;
        }

        int found = caveVoidRegistry.getCount() - beforeCount;
        getLogger().atFine().log("Cave void scan near settlement %s: found %d void(s) (scanned %d-block radius)",
            settlement.getCellKey(), found, scanRadius);
    }

    /**
     * Pre-place 2 surface fallback POI prefabs 200-400 blocks from the settlement.
     * These are placed at settlement creation so they exist before players arrive.
     * Positions are stored on the SettlementRecord and consumed by quests that can't find a cave void.
     */
    private void placeSurfaceFallbackPois(SettlementRecord settlement, World world) {
        int count = 2;
        int centerX = (int) settlement.getPosX();
        int centerZ = (int) settlement.getPosZ();
        java.util.Random rng = new java.util.Random(settlement.getCellKey().hashCode());

        Store<EntityStore> store = world.getEntityStore().getStore();

        for (int i = 0; i < count; i++) {
            final int poiIndex = i;
            double angle = rng.nextDouble() * 2 * Math.PI;
            double dist = 200 + rng.nextDouble() * 200;
            int targetX = centerX + (int) (dist * Math.cos(angle));
            int targetZ = centerZ + (int) (dist * Math.sin(angle));

            getStructurePlacer().placeAtSurface(world, targetX, targetZ, store)
                .whenComplete((placed, error) -> {
                    if (error != null || placed == null) {
                        getLogger().atWarning().log("Surface fallback POI %d failed near settlement %s at (%d, %d)",
                            poiIndex, settlement.getCellKey(), targetX, targetZ);
                        return;
                    }
                    Vector3i entrance = placed.anchorWorld();
                    settlement.addSurfaceFallbackPoi(entrance.getX(), entrance.getY(), entrance.getZ());
                    settlementRegistry.saveAsync();
                });
        }
    }

    public static ComponentType<EntityStore, Nat20NpcData> getNpcDataType() {
        return npcDataType;
    }

    public static ComponentType<EntityStore, Nat20PlayerData> getPlayerDataType() {
        return playerDataType;
    }

    public static ComponentType<EntityStore, Nat20MobLevel> getMobLevelType() {
        return mobLevelType;
    }

    public static ComponentType<EntityStore, Nat20MobAffixes> getMobAffixesType() {
        return mobAffixesType;
    }

    public static ComponentType<EntityStore, Nat20MobGroupMemberComponent> getMobGroupMemberType() {
        return mobGroupMemberType;
    }

    public Nat20MobScaleSystem getMobScaleSystem() {
        return mobScaleSystem;
    }

    public Nat20MobGroupSpawner getMobGroupSpawner() {
        return mobGroupSpawner;
    }

    public PlayerLevelHpSystem getPlayerLevelHpSystem() {
        return playerLevelHpSystem;
    }

    public Nat20XpService getXpService() {
        return xpService;
    }

    public MobScalingConfig getScalingConfig() {
        return scalingConfig;
    }

    public Nat20DamageContributorTracker getContributorTracker() {
        return contributorTracker;
    }

    @Override
    protected void setup() {
        getLogger().atInfo().log("Natural 20 setting up...");

        // Register custom ECS components
        npcDataType = getEntityStoreRegistry().registerComponent(
                Nat20NpcData.class, "nat20_npc_data", Nat20NpcData.CODEC, true);
        playerDataType = getEntityStoreRegistry().registerComponent(
                Nat20PlayerData.class, "nat20_player_data", Nat20PlayerData.CODEC, true);
        mobLevelType = getEntityStoreRegistry().registerComponent(
                Nat20MobLevel.class, "nat20_mob_level", Nat20MobLevel.CODEC, true);
        mobAffixesType = getEntityStoreRegistry().registerComponent(
                Nat20MobAffixes.class, "nat20_mob_affixes", Nat20MobAffixes.CODEC, true);
        mobGroupMemberType = getEntityStoreRegistry().registerComponent(
                Nat20MobGroupMemberComponent.class, "nat20_mob_group_member",
                Nat20MobGroupMemberComponent.CODEC, true);
        // Register custom NPC instruction list action for dialogue
        // Requires Hytale:NPC dependency in manifest.json so NPCPlugin loads first
        NPCPlugin.get().registerCoreComponentType(
                "Nat20StartDialogue",
                () -> new BuilderActionNat20StartDialogue(dialogueManager)
        );

        // Initialize Character Sheet UI manager singleton (Task 6 stub: Task 7 wires the page)
        CharacterSheetManager.init();

        // Register commands
        getCommandRegistry().registerCommand(new Nat20Command());
        getCommandRegistry().registerCommand(new com.chonbosmods.commands.SheetCommand());

        // Register equipment change listener for loot stat modifiers (ECS event system)
        getEntityStoreRegistry().registerSystem(equipmentListener.createSystem());

        // Register ECS event systems for EFFECT/ABILITY affix processing (damage + block break)
        lootSystem.registerSystems(getEntityStoreRegistry());

        // Hook MessagesUpdated to re-inject I18n entries after language reload
        getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.modules.i18n.event.MessagesUpdated.class,
                event -> lootSystem.getItemRegistry().reinjectAllI18n()
        );

        // Start GC cleanup polling
        lootSystem.getGarbageCollector().start();

        // XP/mlvl/ilvl: load config + register scale system
        scalingConfig = MobScalingConfig.load();
        ambientSpawnConfig = AmbientSpawnConfig.load();
        mobScaleSystem = new Nat20MobScaleSystem(scalingConfig);
        getEntityStoreRegistry().registerSystem(mobScaleSystem);
        getEntityStoreRegistry().registerSystem(new Nat20MobDmgScaleSystem(scalingConfig));
        getEntityStoreRegistry().registerSystem(new Nat20MobTintTickSystem());
        mobGroupSpawner = new Nat20MobGroupSpawner(scalingConfig);
        // Dedup system needs the registry, but we register it later (below) because
        // mobGroupRegistry is constructed further down; see "Load POI mob-group registry".
        playerLevelHpSystem = new PlayerLevelHpSystem(scalingConfig);
        xpService = new Nat20XpService(playerLevelHpSystem);
        // Contributor tracking must register BEFORE XP/loot systems so the lethal
        // damage event sees a fresh write first. XP and loot systems also record
        // defensively in case ECS ordering is not strictly guaranteed.
        getEntityStoreRegistry().registerSystem(new Nat20ContributorTrackingSystem(contributorTracker));
        getEntityStoreRegistry().registerSystem(new Nat20XpOnKillSystem(scalingConfig, xpService, contributorTracker, speciesXpRegistry));
        getEntityStoreRegistry().registerSystem(new Nat20MobLootDropSystem(lootSystem, contributorTracker));

        // Register settlement NPC death/respawn system
        getEntityStoreRegistry().registerSystem(new SettlementNpcDeathSystem());

        // Register POI kill tracking system for quest objectives
        getEntityStoreRegistry().registerSystem(new POIKillTrackingSystem());

        // Register FETCH_ITEM pickup detection system for quest objectives
        getEntityStoreRegistry().registerSystem(new FetchItemTrackingSystem());

        // Register COLLECT_RESOURCES inventory counting system for quest objectives
        getEntityStoreRegistry().registerSystem(new CollectResourceTrackingSystem());

        // Register settlement threat detection system (marks attackers as hostile)
        getEntityStoreRegistry().registerSystem(new SettlementThreatSystem());

        // Register combat debug logging system (post-damage inspection)
        getEntityStoreRegistry().registerSystem(new CombatDebugSystem());

        // Register combat particle system (gold crit bursts, red bleed splats)
        getEntityStoreRegistry().registerSystem(new Nat20CombatParticleSystem());

        // Register absorption affix system (Filter Group: redirects damage to mana)
        absorptionSystem = new Nat20AbsorptionSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(absorptionSystem);

        // Register deep wounds affix system (Inspect Group: bleed DOT on melee hit)
        // Unified DOT tick system: all DOTs on an entity share one tick phase
        dotTickSystem = new Nat20DotTickSystem();
        getEntityStoreRegistry().registerSystem(dotTickSystem);

        deepWoundsSystem = new Nat20DeepWoundsSystem(lootSystem, dotTickSystem);
        getEntityStoreRegistry().registerSystem(deepWoundsSystem);

        // Register attack speed affix system (ECS ticking system, runs AFTER TickInteractionManagerSystem)
        attackSpeedSystem = new Nat20AttackSpeedSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(attackSpeedSystem);


        // Register focused mind affix system (ECS ticking: smooth mana regen boost while idle)
        focusedMindSystem = new Nat20FocusedMindSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(focusedMindSystem);

        // Phase 3: persistent score bonus systems
        scoreBonusSystem = new Nat20ScoreBonusSystem(lootSystem, equipmentListener);
        getEntityStoreRegistry().registerSystem(scoreBonusSystem);

        scoreRegenSystem = new Nat20ScoreRegenSystem();
        getEntityStoreRegistry().registerSystem(scoreRegenSystem);

        scoreDamageSystem = new Nat20ScoreDamageSystem();
        getEntityStoreRegistry().registerSystem(scoreDamageSystem);

        critSystem = new Nat20CritSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(critSystem);

        // DEX -> movement speed (bridges DEX modifier to MovementManager baseSpeed)
        movementSpeedSystem = new Nat20MovementSpeedSystem();
        getEntityStoreRegistry().registerSystem(movementSpeedSystem);

        // Phase 5 Batch 1: flat elemental damage (fire/frost/poison/void on hit)
        elementalDamageSystem = new Nat20ElementalDamageSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(elementalDamageSystem);

        // Phase 5 Batch 2: elemental proc DOTs (ignite/cold/infect/corrupt)
        elementalDotSystem = new Nat20ElementalDotSystem(lootSystem, dotTickSystem);
        getEntityStoreRegistry().registerSystem(elementalDotSystem);

        // Phase 5 Batch 3: leech pair (life leech + mana leech)
        lifeLeechSystem = new Nat20LifeLeechSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(lifeLeechSystem);
        manaLeechSystem = new Nat20ManaLeechSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(manaLeechSystem);

        // Phase 5 Batch 4: debuff/curse weapon effects
        viciousMockerySystem = new Nat20ViciousMockerySystem(lootSystem);
        getEntityStoreRegistry().registerSystem(viciousMockerySystem);
        getEntityStoreRegistry().registerSystem(new Nat20ViciousMockeryAmplifySystem(viciousMockerySystem));
        hexSystem = new Nat20HexSystem(lootSystem, dotTickSystem);
        getEntityStoreRegistry().registerSystem(hexSystem);
        getEntityStoreRegistry().registerSystem(new Nat20HexConsumeSystem(hexSystem, dotTickSystem));
        gallantSystem = new Nat20GallantSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(gallantSystem);
        getEntityStoreRegistry().registerSystem(new Nat20GallantReduceSystem(gallantSystem));

        // Phase 5 Batch 5: elemental weakness + resistance
        weaknessApplySystem = new Nat20WeaknessApplySystem(lootSystem);
        getEntityStoreRegistry().registerSystem(weaknessApplySystem);
        getEntityStoreRegistry().registerSystem(new Nat20WeaknessAmplifySystem(weaknessApplySystem));
        getEntityStoreRegistry().registerSystem(new Nat20ResistanceSystem(lootSystem));

        // Phase 5 Batch 6: remaining weapon effects
        getEntityStoreRegistry().registerSystem(new Nat20CrushingBlowSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20BackstabSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20PrecisionSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20BlockProficiencySystem(lootSystem));

        // Phase 5 Batch 7: defensive armor affixes
        getEntityStoreRegistry().registerSystem(new Nat20ThornsSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20EvasionSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20ResilienceSystem(lootSystem));

        // Phase 5 Batch 8: utility armor + on-kill
        getEntityStoreRegistry().registerSystem(new Nat20WaterBreathingSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20LightFootSystem(lootSystem));
        Nat20RallySystem rallySystem = new Nat20RallySystem(lootSystem);
        getEntityStoreRegistry().registerSystem(rallySystem);
        getEntityStoreRegistry().registerSystem(new Nat20RallyAmplifySystem(rallySystem));

        // Tool affixes
        getEntityStoreRegistry().registerSystem(new Nat20HasteSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20ShapeMiningSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20TelekinesisSystem(lootSystem));

        // Clean up on player disconnect
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            UUID uuid = event.getPlayerRef().getUuid();
            dialogueManager.endSession(uuid);
            equipmentListener.clearPlayer(uuid);
            QuestMarkerProvider.INSTANCE.removePlayer(uuid);
            if (poiProximitySystem != null) poiProximitySystem.removePlayer(uuid);
            if (settlementDiscoverySystem != null) settlementDiscoverySystem.removePlayer(uuid);
            CombatDebugSystem.removePlayer(uuid);
            if (absorptionSystem != null) absorptionSystem.removePlayer(uuid);
            if (attackSpeedSystem != null) attackSpeedSystem.removePlayer(uuid);  // clears tracked shift state
            if (focusedMindSystem != null) focusedMindSystem.removePlayer(uuid);  // clears tracked state
            if (movementSpeedSystem != null) movementSpeedSystem.removePlayer(uuid);
            if (scoreRegenSystem != null) scoreRegenSystem.removePlayer(uuid);
            if (hexSystem != null) hexSystem.removePlayer(uuid);
            Nat20ScoreDirtyFlag.removePlayer(uuid);
            // Flag the party registry so offline members render as "Unknown"
            // on any still-online partymate's /sheet view, and so the ghost
            // leader rule observes live presence correctly.
            if (partyRegistry != null) {
                partyRegistry.markOffline(uuid);
                try {
                    partyRegistry.save();
                } catch (java.io.IOException e) {
                    getLogger().atWarning().withCause(e)
                        .log("Failed to persist party registry on disconnect");
                }
            }
        });

        // Restore quest waypoint markers on player connect and register for POI proximity tracking
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            UUID uuid = event.getPlayer().getUuid();
            Nat20PlayerData data = event.getPlayerRef().getStore()
                    .getComponent(event.getPlayerRef(), getPlayerDataType());
            if (data != null) {
                // Bind the runtime UUID onto the player data component so the
                // store-backed QuestStateManager can resolve the player on
                // every subsequent read / mutation. This MUST happen before
                // migration below, and before any QuestMarkerProvider /
                // sweep call that might read active quests.
                data.setPlayerUuid(uuid);

                // One-shot migration: move any legacy questFlags active quests
                // into the party-quest store under accepters=[self], then wipe
                // the legacy key. Idempotent: re-login is a no-op.
                java.util.Map<String, QuestInstance> legacy =
                    QuestStateManager.readLegacyActiveQuests(data);
                if (!legacy.isEmpty()) {
                    partyQuestStore.migratePlayer(uuid, legacy);
                    QuestStateManager.clearLegacyActiveQuests(data);
                    try {
                        partyQuestStore.save();
                    } catch (java.io.IOException e) {
                        getLogger().atWarning().withCause(e)
                            .log("Failed to persist party-quest store after migration");
                    }
                }

                QuestMarkerProvider.refreshMarkers(uuid, data);
                // Stale-sweep POI mob-group records: remove any whose quest is no longer active.
                // Runs here instead of plugin init because Nat20PlayerData is only readable once
                // the player's entity is in the store.
                sweepStaleMobGroupRecords(uuid, data);
            }

            // Cache display name so offline partymates can still render a
            // name instead of "Unknown" once this player disconnects later.
            partyRegistry.recordName(uuid, event.getPlayer().getDisplayName());

            // Ensure the player has a party (default size-1) and is flagged
            // online so the ghost-leader rule can fire correctly on long-gone
            // leaders. Persist if a new party was created.
            partyRegistry.getParty(uuid);
            partyRegistry.markOnline(uuid);
            try {
                partyRegistry.save();
            } catch (java.io.IOException e) {
                getLogger().atWarning().withCause(e)
                    .log("Failed to persist party registry on player ready");
            }
            if (poiProximitySystem != null) poiProximitySystem.addPlayer(uuid);
            if (settlementDiscoverySystem != null) settlementDiscoverySystem.addPlayer(uuid);
            // focusedMindSystem is an ECS ticking system, no manual player tracking needed
            // attackSpeedSystem is an ECS ticking system, no manual player tracking needed

            // Apply level-derived max-HP modifier on connect.
            if (playerLevelHpSystem != null) {
                playerLevelHpSystem.updatePlayerMaxHp(event.getPlayerRef(), event.getPlayerRef().getStore());
            }

            // Mark player dirty so Phase 3 score bonuses are applied on connect
            Nat20ScoreDirtyFlag.markDirty(uuid);
        });

        // Register quest POI marker provider on every world
        getEventRegistry().registerGlobal(AddWorldEvent.class, event -> {
                World world = event.getWorld();
                world.getWorldMapManager()
                        .addMarkerProvider("nat20_quests", QuestMarkerProvider.INSTANCE);
                world.getWorldConfig().setPvpEnabled(true);
                stripForgottenTempleGatewayMarkers(world);
        });

    }

    @Override
    protected void start() {
        getLogger().atInfo().log("Natural 20 loading prefabs...");

        // Resolve Nat20 prefab marker block IDs. Deferred to start() because
        // BlockType.getAssetMap() isn't populated until after every plugin's
        // setup() has run (vanilla asset modules load their maps during setup,
        // and we set up before them).
        Nat20PrefabConstants.resolve();

        // Load prefabs: assets are available by start()
        placer.init();

        // Settlement registry. Data file is rebound to a world-scoped path in the
        // first-chunk-load hook below, so this initial path is only a placeholder
        // (nothing is loaded from disk at plugin start).
        settlementRegistry = new SettlementRegistry(getDataDirectory());

        // Load POI mob-group registry + spawn coordinator. Data file is rebound to a
        // world-scoped path in the first-chunk-load hook below, so this initial path is
        // only a placeholder (nothing is loaded from disk at plugin start).
        mobGroupRegistry = new Nat20MobGroupRegistry(getDataDirectory());
        poiGroupSpawnCoordinator = new POIGroupSpawnCoordinator(mobGroupRegistry, mobGroupSpawner);
        getEntityStoreRegistry().registerSystem(new Nat20MobGroupDedupSystem(mobGroupRegistry));
        getEntityStoreRegistry().registerSystem(new Nat20MobGroupCombatStampSystem());
        getEntityStoreRegistry().registerSystem(new Nat20MobGroupLeashSystem(mobGroupRegistry));

        // Chest affix-loot injection. Registry data file is rebound to a world-scoped
        // path in the first-chunk-load hook below; initial path is a placeholder.
        Nat20ChestLootConfig chestLootConfig = Nat20ChestLootConfig.load();
        chestRollRegistry = new Nat20ChestRollRegistry(getDataDirectory());
        Nat20ChestLootRoller chestLootRoller = new Nat20ChestLootRoller(chestLootConfig);
        Nat20ChestLootPicker chestLootPicker = new Nat20ChestLootPicker(lootSystem);
        QuestChestPlacer.setChestRollRegistry(chestRollRegistry);
        getEntityStoreRegistry().registerSystem(new Nat20ChestAffixInjectionSystem(
                chestLootConfig, chestLootRoller, chestRollRegistry, scalingConfig, chestLootPicker));
        com.hypixel.hytale.logger.HytaleLogger.get("Nat20|ChestInject").atInfo()
                .log("Chest affix injection wired: %d block types; chance=%.2f",
                        chestLootConfig.blockTypeCount(), chestLootConfig.getChance());

        // Enumerate native hostile mob pool by scanning NPCPlugin roles
        hostilePool.initialize();

        // Load biome/zone theme tables (loads mob_themes.json from resources)
        mobThemeRegistry.initialize();

        // Load per-species XP weight table (HP/damage-derived multipliers)
        speciesXpRegistry.initialize();

        // Load cave void registry and scanner. Like mob_groups.json, the save file is
        // rebound to a world-scoped path in the first-chunk-load hook below.
        Path caveVoidPath = getDataDirectory().resolve("cave_voids.json");
        caveVoidRegistry = new CaveVoidRegistry(caveVoidPath);
        caveVoidScanner = new CaveVoidScanner(caveVoidRegistry);
        structurePlacer = new UndergroundStructurePlacer();

        // Register worldgen settlement listener
        SettlementWorldGenListener worldGenListener = new SettlementWorldGenListener(settlementRegistry, placer);
        MobGroupChunkListener mobGroupChunkListener =
                new MobGroupChunkListener(mobGroupRegistry, mobGroupSpawner);
        AmbientAnchorFinder ambientAnchorFinder = new AmbientAnchorFinder(
                caveVoidRegistry, settlementRegistry, mobGroupRegistry, ambientSpawnConfig);
        ambientSpawnSystem = new AmbientSpawnSystem(
                ambientSpawnConfig, ambientAnchorFinder, mobGroupRegistry,
                mobGroupSpawner, mobGroupChunkListener);

        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, event -> {
            var chunk = event.getChunk();
            if (defaultWorld == null) {
                defaultWorld = chunk.getWorld();
                initWorldScopedRegistries(defaultWorld);
            }
            // WorldChunk.getX()/getZ() return chunk coordinates: multiply by 32 for block coords
            int chunkBlockX = chunk.getX() * 32;
            int chunkBlockZ = chunk.getZ() * 32;
            worldGenListener.onChunkLoad(chunk.getWorld(), chunkBlockX, chunkBlockZ);
            mobGroupChunkListener.onChunkLoad(chunk.getWorld(), chunkBlockX, chunkBlockZ);
            ambientSpawnSystem.onChunkLoad(chunk.getWorld(), chunkBlockX, chunkBlockZ);
        });

        // POI population listener (writes spawn descriptors, provides spawnMobs for proximity system)
        poiPopulationListener = new POIPopulationListener();

        // POI proximity system: checks player distance to quest POIs every second
        poiProximitySystem = new POIProximitySystem(poiGroupSpawnCoordinator, mobGroupRegistry);
        settlementDiscoverySystem = new SettlementDiscoverySystem();
        poiProximityExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat20-poi-proximity");
            t.setDaemon(true);
            return t;
        });
        poiProximityExecutor.scheduleAtFixedRate(() -> {
            World w = getDefaultWorld();
            if (w != null) {
                w.execute(() -> {
                    poiProximitySystem.tick(w);
                    settlementDiscoverySystem.tick(w);
                    QuestMarkerManager.INSTANCE.tickMarkers(w);
                });
            }
        }, 5, 1, TimeUnit.SECONDS);

        // Ambient decay sweep: prune ambient groups no player has visited in decayWindowMillis.
        poiProximityExecutor.scheduleAtFixedRate(() -> {
            World w = getDefaultWorld();
            if (w != null) {
                w.execute(() -> ambientSpawnSystem.tickDecay(w));
            }
        }, ambientSpawnConfig.decaySweepIntervalMillis(),
           ambientSpawnConfig.decaySweepIntervalMillis(),
           TimeUnit.MILLISECONDS);

        // Load dialogue files from plugin data directory
        dialogueLoader.loadAll(getDataDirectory().resolve("dialogues"));

        // Load loot system configs. The itemRegistry's persistent nat20_items.json is
        // initialized separately from initWorldScopedRegistries so it lives under the
        // current world's save dir rather than the plugin data dir.
        lootSystem.loadAll(getDataDirectory().resolve("loot"));

        // Construct party + party-quest + invite registries. They are bound
        // to a world-scoped save directory inside initWorldScopedRegistries
        // when the first chunk loads, matching the settlement / mob-group
        // pattern. At setup time the registries are empty; disk loading is
        // strictly per-world.
        partyQuestStore = new Nat20PartyQuestStore();
        partyRegistry = new Nat20PartyRegistry();
        partyInviteRegistry = new Nat20PartyInviteRegistry();

        // Initialize quest system
        questSystem = new QuestSystem(settlementRegistry, partyQuestStore);
        questSystem.loadTemplates(getDataDirectory().resolve("quests"));
        // Note: settlementRegistry is world-scoped and still empty at this
        // point (loaded on first chunk-load by initWorldScopedRegistries).
        // The real regeneration for persisted settlements happens there. We
        // still call here for the degenerate case of a server that somehow
        // has settlements before any world loads; it's effectively a no-op.
        regenerateAllSettlementTopics();

        // Periodic NPC state sync (every 60s)
        npcSyncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat20-npc-sync");
            t.setDaemon(true);
            return t;
        });
        npcSyncExecutor.scheduleAtFixedRate(() -> {
            syncAllNpcState();
            cleanupGhostNpcs();
        }, 60, 60, TimeUnit.SECONDS);

        getLogger().atInfo().log("Natural 20 v" + getManifest().getVersion() + " started!");
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("Natural 20 shutting down...");
        if (poiProximityExecutor != null) {
            poiProximityExecutor.shutdownNow();
        }
        if (npcSyncExecutor != null) {
            npcSyncExecutor.shutdownNow();
        }
        // Clear marker state
        QuestMarkerManager.INSTANCE.clear();
        if (settlementRegistry != null) {
            // Final sync before save
            syncAllNpcState();
            settlementRegistry.saveAsync();
        }
        if (caveVoidRegistry != null) {
            caveVoidRegistry.saveAsync().join();
        }
    }

    /**
     * Strip stale Forgotten Temple gateway waypoints from a world's persistent
     * BlockMapMarkers resource. The portal is not placed in Natural 20 worlds,
     * but the vanilla block's map marker can survive in save files created
     * before its removal.
     */
    private void stripForgottenTempleGatewayMarkers(World world) {
        world.execute(() -> {
            try {
                var store = world.getChunkStore().getStore();
                BlockMapMarkersResource resource = store.getResource(
                        BlockMapMarkersResource.getResourceType());
                if (resource == null) return;
                List<Vector3i> toRemove = new ArrayList<>();
                for (var data : resource.getMarkers().values()) {
                    if ("Temple_Gateway.png".equals(data.getIcon())
                            || "server.items.Forgotten_Temple_Portal_Enter.name"
                                    .equals(data.getName())) {
                        toRemove.add(data.getPosition());
                    }
                }
                for (Vector3i pos : toRemove) {
                    resource.removeMarker(pos);
                }
                if (!toRemove.isEmpty()) {
                    getLogger().atInfo().log(
                            "Stripped %d Forgotten Temple gateway marker(s) from world %s",
                            toRemove.size(), world.getName());
                }
            } catch (Exception e) {
                getLogger().atWarning().withCause(e).log(
                        "Failed to strip Forgotten Temple gateway markers");
            }
        });
    }

    private List<String> deriveNearbyNames(SettlementRecord settlement) {
        List<String> names = new ArrayList<>();
        for (SettlementRecord other : settlementRegistry.getAll().values()) {
            if (!other.getCellKey().equals(settlement.getCellKey())) {
                names.add(other.deriveName());
            }
        }
        return names;
    }

    /**
     * Spatial sweep for ghost NPC entities: world entities with Nat20NpcData that
     * aren't tracked in the settlement registry. These are left behind by native
     * chunk persistence when a respawn created a new entity with a different UUID.
     * Uses TargetUtil.getAllEntitiesInSphere to find entities near each settlement,
     * then removes any whose UUID doesn't match a registry entry.
     */
    /**
     * Remove POI mob-group records whose quest is no longer active for the given player.
     * Runs on {@code PlayerReadyEvent} since {@link Nat20PlayerData} is only accessible
     * once the player's entity is loaded into the store.
     *
     * <p>No world cleanup: any orphan mobs still in the world decay into ordinary hostiles.
     */
    private void sweepStaleMobGroupRecords(UUID playerUuid, Nat20PlayerData data) {
        if (mobGroupRegistry == null || questSystem == null) return;
        Map<String, QuestInstance> active = questSystem.getStateManager().getActiveQuests(data);
        int removed = 0;
        for (com.chonbosmods.quest.poi.MobGroupRecord record : mobGroupRegistry.forOwner(playerUuid)) {
            if (!active.containsKey(record.getQuestId())) {
                mobGroupRegistry.remove(record.getGroupKey());
                removed++;
            }
        }
        if (removed > 0) {
            getLogger().atInfo().log("Swept %d stale POI mob-group record(s) for player %s",
                    removed, playerUuid);
        }
    }

    private void cleanupGhostNpcs() {
        SettlementRegistry registry = getSettlementRegistry();
        if (registry == null) return;

        for (SettlementRecord settlement : registry.getAll().values()) {
            UUID worldUUID = settlement.getWorldUUID();
            World world = registry.getCachedWorld(worldUUID);
            if (world == null) continue;

            String cellKey = settlement.getCellKey();

            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();

                // Collect all valid UUIDs for this settlement
                Set<UUID> validUUIDs = new HashSet<>();
                for (NpcRecord npc : settlement.getNpcs()) {
                    if (npc.getEntityUUID() != null) validUUIDs.add(npc.getEntityUUID());
                }

                // Spatial sweep around settlement center
                Vector3d center = new Vector3d(
                        settlement.getPosX(), settlement.getPosY(), settlement.getPosZ());
                try {
                    var nearby = TargetUtil.getAllEntitiesInSphere(center, 60.0, store);
                    for (Ref<EntityStore> ref : nearby) {
                        if (!ref.isValid()) continue;

                        Nat20NpcData data = store.getComponent(ref, getNpcDataType());
                        if (data == null) continue;
                        if (!cellKey.equals(data.getSettlementCellKey())) continue;

                        NPCEntity npcEntity = store.getComponent(ref,
                                NPCEntity.getComponentType());
                        if (npcEntity == null) continue;

                        if (!validUUIDs.contains(npcEntity.getUuid())) {
                            store.removeEntity(ref, RemoveReason.REMOVE);
                            getLogger().atFine().log("Removed ghost NPC: %s (%s) UUID %s in %s",
                                    data.getGeneratedName(), data.getRoleName(),
                                    npcEntity.getUuid(), cellKey);
                        }
                    }
                } catch (Exception e) {
                    getLogger().atWarning().withCause(e).log(
                            "Error during ghost NPC sweep for settlement %s", cellKey);
                }
            });
        }
    }

    /**
     * Sync all live NPC Nat20NpcData back to their NpcRecords.
     * Called periodically to ensure external persistence stays current.
     */
    private void syncAllNpcState() {
        SettlementRegistry registry = getSettlementRegistry();
        if (registry == null) return;

        for (SettlementRecord settlement : registry.getAll().values()) {
            UUID worldUUID = settlement.getWorldUUID();
            World world = registry.getCachedWorld(worldUUID);
            if (world == null) continue;

            world.execute(() -> {
                var store = world.getEntityStore().getStore();
                boolean dirty = false;

                for (NpcRecord npc : settlement.getNpcs()) {
                    if (npc.getEntityUUID() == null) continue;

                    try {
                        Ref<EntityStore> ref = world.getEntityRef(npc.getEntityUUID());
                        if (ref == null) continue;

                        Nat20NpcData data = store.getComponent(ref, getNpcDataType());
                        if (data == null) continue;

                        // Sync mutable fields
                        if (data.getDefaultDisposition() != npc.getDisposition()) {
                            npc.setDisposition(data.getDefaultDisposition());
                            dirty = true;
                        }
                        String ds = data.getDialogueState();
                        if (!java.util.Objects.equals(ds, npc.getDialogueState())) {
                            npc.setDialogueState(ds);
                            dirty = true;
                        }
                        Map<String, String> flags = data.getFlags();
                        if (!java.util.Objects.equals(flags, npc.getFlags())) {
                            npc.setFlags(flags);
                            dirty = true;
                        }
                    } catch (Exception ignored) {}
                }

                if (dirty) {
                    registry.saveAsync();
                }
            });
        }
    }
}
