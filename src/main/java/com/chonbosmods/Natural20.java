package com.chonbosmods;

import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.cave.CaveVoidScanner;
import com.chonbosmods.cave.UndergroundStructurePlacer;
import com.chonbosmods.commands.Nat20Command;
import com.chonbosmods.data.Nat20GlobalData;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
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
import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.settlement.SettlementThreatSystem;
import com.chonbosmods.settlement.SettlementWorldGenListener;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Map;
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
    private java.util.concurrent.ScheduledExecutorService poiProximityExecutor;
    private Config<Nat20GlobalData> globalConfig;
    private java.util.concurrent.ScheduledExecutorService npcSyncExecutor;

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
     * Called when a new settlement is created during world generation.
     * Generates procedural topic dialogue graphs and scans for nearby cave voids.
     */
    public void onSettlementCreated(SettlementRecord settlement, World world) {
        if (questSystem != null) {
            var topicGraphs = questSystem.getTopicGenerator().generate(settlement);
            dialogueLoader.registerGeneratedGraphs(topicGraphs);
        }

        // Scan for cave voids near the settlement so POI quests have targets
        // Batched on the world thread: scans one row of chunks per tick to avoid blocking
        if (caveVoidScanner != null && world != null) {
            int centerX = (int) settlement.getPosX();
            int centerZ = (int) settlement.getPosZ();
            String cellKey = settlement.getCellKey();
            int scanRadius = 150;
            int chunkRadius = scanRadius / 32;
            int centerChunkX = Math.floorDiv(centerX, 32);
            int centerChunkZ = Math.floorDiv(centerZ, 32);
            int startCX = centerChunkX - chunkRadius;
            int endCX = centerChunkX + chunkRadius;
            int startCZ = centerChunkZ - chunkRadius;
            int endCZ = centerChunkZ + chunkRadius;
            int beforeCount = caveVoidRegistry.getCount();
            int maxVoids = 3;
            scanChunkRow(world, cellKey, startCX, endCX, startCX, startCZ, endCZ, beforeCount, maxVoids);
        }
    }

    /**
     * Scans one row of chunks (one X value, all Z) for cave voids, then defers the next row
     * to the next world tick. Stops early if maxVoids new voids have been found.
     */
    private void scanChunkRow(World world, String cellKey, int cx, int endCX,
                               int startCX, int startCZ, int endCZ,
                               int beforeCount, int maxVoids) {
        if (cx > endCX || caveVoidRegistry.getCount() - beforeCount >= maxVoids) {
            int found = caveVoidRegistry.getCount() - beforeCount;
            getLogger().atInfo().log("Cave void scan near settlement %s: found %d void(s)", cellKey, found);
            return;
        }
        for (int cz = startCZ; cz <= endCZ; cz++) {
            caveVoidScanner.scanChunk(world, cx * 32, cz * 32);
            if (caveVoidRegistry.getCount() - beforeCount >= maxVoids) break;
        }
        // Defer next row to next tick so we don't hog the world thread
        world.execute(() -> scanChunkRow(world, cellKey, cx + 1, endCX, startCX, startCZ, endCZ, beforeCount, maxVoids));
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

        // Clean up on player disconnect
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            UUID uuid = event.getPlayerRef().getUuid();
            dialogueManager.endSession(uuid);
            equipmentListener.clearPlayer(uuid);
            QuestMarkerProvider.INSTANCE.removePlayer(uuid);
            if (poiProximitySystem != null) poiProximitySystem.removePlayer(uuid);
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
        poiProximityExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat20-poi-proximity");
            t.setDaemon(true);
            return t;
        });
        poiProximityExecutor.scheduleAtFixedRate(() -> {
            World w = getDefaultWorld();
            if (w != null) {
                w.execute(() -> poiProximitySystem.tick(w));
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
        dialogueManager.setTopicPoolRegistry(questSystem.getTopicPoolRegistry());

        // Generate procedural topics for all existing settlements
        for (SettlementRecord settlement : settlementRegistry.getAll().values()) {
            var topicGraphs = questSystem.getTopicGenerator().generate(settlement);
            dialogueLoader.registerGeneratedGraphs(topicGraphs);
        }
        getLogger().atInfo().log("Generated procedural topics for %d settlement(s)", settlementRegistry.getAll().size());

        // Periodic NPC state sync (every 60s)
        npcSyncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat20-npc-sync");
            t.setDaemon(true);
            return t;
        });
        npcSyncExecutor.scheduleAtFixedRate(this::syncAllNpcState, 60, 60, TimeUnit.SECONDS);

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
