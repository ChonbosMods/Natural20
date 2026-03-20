package com.chonbosmods;

import com.chonbosmods.commands.Nat20Command;
import com.chonbosmods.data.Nat20GlobalData;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.dialogue.DialogueLoader;
import com.chonbosmods.dialogue.DialogueManager;
import com.chonbosmods.loot.Nat20EquipmentListener;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.npc.BuilderActionNat20StartDialogue;
import com.chonbosmods.npc.Nat20NpcManager;
import com.chonbosmods.settlement.SettlementNpcDeathSystem;
import com.chonbosmods.settlement.SettlementPlacer;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.settlement.SettlementThreatSystem;
import com.chonbosmods.settlement.SettlementWorldGenListener;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
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
    private Config<Nat20GlobalData> globalConfig;

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

    /**
     * Called when a new settlement is created during world generation.
     * Generates procedural topic dialogue graphs for the settlement's NPCs.
     */
    public void onSettlementCreated(SettlementRecord settlement) {
        if (questSystem != null) {
            var topicGraphs = questSystem.getTopicGenerator().generate(settlement);
            dialogueLoader.registerGeneratedGraphs(topicGraphs);
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

        // Register equipment change listener for loot stat modifiers
        equipmentListener.register(getEventRegistry());

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

        // Register settlement threat detection system (marks attackers as hostile)
        getEntityStoreRegistry().registerSystem(new SettlementThreatSystem());

        // Clean up on player disconnect
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            dialogueManager.endSession(event.getPlayerRef().getUuid());
            equipmentListener.clearPlayer(event.getPlayerRef().getUuid());
        });
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("Natural 20 loading prefabs...");

        // Load prefabs: assets are available by start()
        placer.init();

        // Load settlement registry
        settlementRegistry = new SettlementRegistry(getDataDirectory());
        settlementRegistry.load();

        // Register worldgen settlement listener
        SettlementWorldGenListener worldGenListener = new SettlementWorldGenListener(settlementRegistry, placer);
        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, event -> {
            var chunk = event.getChunk();
            // WorldChunk.getX()/getZ() return chunk coordinates: multiply by 32 for block coords
            int chunkBlockX = chunk.getX() * 32;
            int chunkBlockZ = chunk.getZ() * 32;
            worldGenListener.onChunkLoad(chunk.getWorld(), chunkBlockX, chunkBlockZ);
        });

        // Load dialogue files from plugin data directory
        dialogueLoader.loadAll(getDataDirectory().resolve("dialogues"));

        // Load loot system configs
        lootSystem.loadAll(getDataDirectory().resolve("loot"));

        // Rehydrate persisted unique items and inject I18n entries
        lootSystem.getItemRegistry().rehydrateAll();

        // Initialize quest system
        questSystem = new QuestSystem(settlementRegistry);
        questSystem.loadTemplates(getDataDirectory().resolve("quests"));

        // Generate procedural topics for all existing settlements
        for (SettlementRecord settlement : settlementRegistry.getAll().values()) {
            var topicGraphs = questSystem.getTopicGenerator().generate(settlement);
            dialogueLoader.registerGeneratedGraphs(topicGraphs);
        }
        getLogger().atInfo().log("Generated procedural topics for %d settlement(s)", settlementRegistry.getAll().size());

        getLogger().atInfo().log("Natural 20 v" + getManifest().getVersion() + " started!");
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("Natural 20 shutting down...");
        if (settlementRegistry != null) {
            settlementRegistry.saveAsync();
        }
    }
}
