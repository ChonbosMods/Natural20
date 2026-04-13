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
import com.chonbosmods.combat.Nat20BlockProficiencySystem;
import com.chonbosmods.combat.Nat20CrushingBlowSystem;
import com.chonbosmods.combat.Nat20EvasionSystem;
import com.chonbosmods.combat.Nat20RallySystem;
import com.chonbosmods.combat.Nat20ResilienceSystem;
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
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.dialogue.DialogueLoader;
import com.chonbosmods.dialogue.DialogueManager;
import com.chonbosmods.loot.Nat20EquipmentListener;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.quest.CollectResourceTrackingSystem;
import com.chonbosmods.quest.FetchItemTrackingSystem;
import com.chonbosmods.quest.POIKillTrackingSystem;
import com.chonbosmods.quest.POIPopulationListener;
import com.chonbosmods.quest.POIProximitySystem;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.npc.BuilderActionNat20StartDialogue;
import com.chonbosmods.npc.Nat20NpcManager;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementNpcDeathSystem;
import com.chonbosmods.settlement.SettlementPlacer;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementDiscoverySystem;
import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.settlement.SettlementThreatSystem;
import com.chonbosmods.settlement.SettlementWorldGenListener;
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
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Vector3d;

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

    private final SettlementPlacer placer = new SettlementPlacer();
    private final Nat20NpcManager npcManager = new Nat20NpcManager();
    private final DialogueActionRegistry actionRegistry = new DialogueActionRegistry();
    private final DialogueLoader dialogueLoader = new DialogueLoader();
    private final DialogueManager dialogueManager = new DialogueManager(dialogueLoader, actionRegistry);
    private final Nat20LootSystem lootSystem = new Nat20LootSystem();
    private QuestSystem questSystem;
    private final Nat20EquipmentListener equipmentListener = new Nat20EquipmentListener(lootSystem);
    private SettlementRegistry settlementRegistry;
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

    public SettlementRegistry getSettlementRegistry() {
        return settlementRegistry;
    }

    public CaveVoidRegistry getCaveVoidRegistry() { return caveVoidRegistry; }

    public CaveVoidScanner getCaveVoidScanner() { return caveVoidScanner; }

    public UndergroundStructurePlacer getStructurePlacer() { return structurePlacer; }

    public POIPopulationListener getPOIPopulationListener() { return poiPopulationListener; }

    public POIProximitySystem getPOIProximitySystem() { return poiProximitySystem; }

    private volatile World defaultWorld;

    public World getDefaultWorld() {
        return defaultWorld;
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
        getLogger().atInfo().log("Cave void scan near settlement %s: found %d void(s) (scanned %d-block radius)",
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
                .whenComplete((entrance, error) -> {
                    if (error != null || entrance == null) {
                        getLogger().atWarning().log("Surface fallback POI %d failed near settlement %s at (%d, %d)",
                            poiIndex, settlement.getCellKey(), targetX, targetZ);
                        return;
                    }
                    settlement.addSurfaceFallbackPoi(entrance.getX(), entrance.getY(), entrance.getZ());
                    getLogger().atInfo().log("Surface fallback POI placed at (%d, %d, %d) for settlement %s",
                        entrance.getX(), entrance.getY(), entrance.getZ(), settlement.getCellKey());
                    // Save settlement with updated POI list
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

    @Override
    protected void setup() {
        getLogger().atInfo().log("Natural 20 setting up...");

        // Register custom ECS components
        npcDataType = getEntityStoreRegistry().registerComponent(
                Nat20NpcData.class, "nat20_npc_data", Nat20NpcData.CODEC, true);
        playerDataType = getEntityStoreRegistry().registerComponent(
                Nat20PlayerData.class, "nat20_player_data", Nat20PlayerData.CODEC, true);
        // Register custom NPC instruction list action for dialogue
        // Requires Hytale:NPC dependency in manifest.json so NPCPlugin loads first
        NPCPlugin.get().registerCoreComponentType(
                "Nat20StartDialogue",
                () -> new BuilderActionNat20StartDialogue(dialogueManager)
        );

        // Register commands
        getCommandRegistry().registerCommand(new Nat20Command());

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
        hexSystem = new Nat20HexSystem(lootSystem);
        getEntityStoreRegistry().registerSystem(hexSystem);
        getEntityStoreRegistry().registerSystem(new Nat20HexConsumeSystem(hexSystem));
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
        getEntityStoreRegistry().registerSystem(new Nat20BlockProficiencySystem(lootSystem));

        // Phase 5 Batch 7: defensive armor affixes
        getEntityStoreRegistry().registerSystem(new Nat20ThornsSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20EvasionSystem(lootSystem));
        getEntityStoreRegistry().registerSystem(new Nat20ResilienceSystem(lootSystem));

        // Phase 5 Batch 8: utility armor + on-kill
        // Water Breathing, Light Foot: affix JSONs registered, systems deferred pending
        // SDK investigation (breath stat name, sprint stamina cost stat)
        getEntityStoreRegistry().registerSystem(new Nat20RallySystem(lootSystem));

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
        });

        // Restore quest waypoint markers on player connect and register for POI proximity tracking
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            UUID uuid = event.getPlayer().getUuid();
            Nat20PlayerData data = event.getPlayerRef().getStore()
                    .getComponent(event.getPlayerRef(), getPlayerDataType());
            if (data != null) {
                QuestMarkerProvider.refreshMarkers(uuid, data);
            }
            if (poiProximitySystem != null) poiProximitySystem.addPlayer(uuid);
            if (settlementDiscoverySystem != null) settlementDiscoverySystem.addPlayer(uuid);
            // focusedMindSystem is an ECS ticking system, no manual player tracking needed
            // attackSpeedSystem is an ECS ticking system, no manual player tracking needed

            // Mark player dirty so Phase 3 score bonuses are applied on connect
            Nat20ScoreDirtyFlag.markDirty(uuid);
        });

        // Register quest POI marker provider on every world
        getEventRegistry().registerGlobal(AddWorldEvent.class, event ->
                event.getWorld().getWorldMapManager()
                        .addMarkerProvider("nat20_quests", QuestMarkerProvider.INSTANCE));
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("Natural 20 loading prefabs...");

        // Load prefabs: assets are available by start()
        placer.init();

        // Load settlement registry
        settlementRegistry = new SettlementRegistry(getDataDirectory());
        settlementRegistry.load();

        // Load cave void registry and scanner
        Path caveVoidPath = getDataDirectory().resolve("cave_voids.json");
        caveVoidRegistry = new CaveVoidRegistry(caveVoidPath);
        caveVoidRegistry.load();
        caveVoidScanner = new CaveVoidScanner(caveVoidRegistry);
        structurePlacer = new UndergroundStructurePlacer();

        // Register worldgen settlement listener
        SettlementWorldGenListener worldGenListener = new SettlementWorldGenListener(settlementRegistry, placer);
        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, event -> {
            var chunk = event.getChunk();
            if (defaultWorld == null) {
                defaultWorld = chunk.getWorld();
            }
            // WorldChunk.getX()/getZ() return chunk coordinates: multiply by 32 for block coords
            int chunkBlockX = chunk.getX() * 32;
            int chunkBlockZ = chunk.getZ() * 32;
            worldGenListener.onChunkLoad(chunk.getWorld(), chunkBlockX, chunkBlockZ);
        });

        // POI population listener (writes spawn descriptors, provides spawnMobs for proximity system)
        poiPopulationListener = new POIPopulationListener();

        // POI proximity system: checks player distance to quest POIs every second
        poiProximitySystem = new POIProximitySystem();
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

        // Load dialogue files from plugin data directory
        dialogueLoader.loadAll(getDataDirectory().resolve("dialogues"));

        // Load loot system configs
        lootSystem.loadAll(getDataDirectory().resolve("loot"));

        // Rehydrate persisted unique items and inject I18n entries
        lootSystem.getItemRegistry().rehydrateAll();

        // Initialize quest system
        questSystem = new QuestSystem(settlementRegistry);
        questSystem.loadTemplates(getDataDirectory().resolve("quests"));
        // Generate procedural topics for all existing settlements. Iterate in
        // chronological placedAt order (tiebreak on cellKey) so the shared
        // per-world dedup evolves identically to how it did during original
        // world-gen: this is what makes dialogue regeneration restart-stable.
        List<SettlementRecord> orderedSettlements = new ArrayList<>(
            settlementRegistry.getAll().values());
        orderedSettlements.sort(
            java.util.Comparator.comparingLong(SettlementRecord::getPlacedAt)
                .thenComparing(SettlementRecord::getCellKey));
        java.util.Set<UUID> resetWorlds = new java.util.HashSet<>();
        for (SettlementRecord settlement : orderedSettlements) {
            UUID worldId = settlement.getWorldUUID();
            if (worldId != null && resetWorlds.add(worldId)) {
                questSystem.getTopicGenerator().resetDedupForWorld(worldId);
            }
            var topicGraphs = questSystem.getTopicGenerator().generate(settlement, deriveNearbyNames(settlement));
            dialogueLoader.registerGeneratedGraphs(topicGraphs);
        }
        getLogger().atInfo().log("Generated procedural topics for %d settlement(s)", orderedSettlements.size());

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
                            getLogger().atInfo().log("Removed ghost NPC: %s (%s) UUID %s in %s",
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
