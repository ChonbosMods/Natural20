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
import com.chonbosmods.npc.BuilderActionNat20StartDialogue;
import com.chonbosmods.npc.Nat20NpcManager;
import com.chonbosmods.settlement.SettlementPlacer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.meta.state.BlockMapMarkersResource;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.spawning.suppression.SpawnSuppressorEntry;
import com.hypixel.hytale.server.spawning.suppression.component.SpawnSuppressionController;

import javax.annotation.Nonnull;
import java.util.UUID;

public class Natural20 extends JavaPlugin {

    // Fixed UUID for our gateway suppression zone: deterministic so we never duplicate
    private static final UUID GATEWAY_SUPPRESSION_UUID =
            UUID.fromString("00000a20-0000-4000-a000-000000000001");

    private static Natural20 instance;

    private static ComponentType<EntityStore, Nat20NpcData> npcDataType;
    private static ComponentType<EntityStore, Nat20PlayerData> playerDataType;

    private final SettlementPlacer placer = new SettlementPlacer();
    private final Nat20NpcManager npcManager = new Nat20NpcManager();
    private final DialogueActionRegistry actionRegistry = new DialogueActionRegistry();
    private final DialogueLoader dialogueLoader = new DialogueLoader();
    private final DialogueManager dialogueManager = new DialogueManager(dialogueLoader, actionRegistry);
    private final Nat20LootSystem lootSystem = new Nat20LootSystem();
    private final Nat20EquipmentListener equipmentListener = new Nat20EquipmentListener(lootSystem);
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

        // Clean up on player disconnect
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            dialogueManager.endSession(event.getPlayerRef().getUuid());
            equipmentListener.clearPlayer(event.getPlayerRef().getUuid());
        });
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("Natural 20 loading prefabs...");

        // Load prefabs — assets are available by start()
        placer.init();

        // Load dialogue files from plugin data directory
        dialogueLoader.loadAll(getDataDirectory().resolve("dialogues"));

        // Load loot system configs
        lootSystem.loadAll(getDataDirectory().resolve("loot"));

        // Set world spawn to the gateway portal area
        configureGatewaySpawn();

        getLogger().atInfo().log("Natural 20 v" + getManifest().getVersion() + " started!");
    }

    /**
     * Finds the Forgotten Temple portal in the default world via BlockMapMarkers,
     * sets the world spawn near it, and places a spawn suppression zone to keep
     * the gateway area clear of vanilla NPCs.
     */
    private void configureGatewaySpawn() {
        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                getLogger().atWarning().log("No default world found, skipping gateway spawn setup");
                return;
            }

            // Read BlockMapMarkers from the world's chunk store to find the portal
            BlockMapMarkersResource markers = world.getChunkStore().getStore().getResource(
                    BlockMapMarkersResource.getResourceType());
            if (markers == null) {
                getLogger().atWarning().log("No BlockMapMarkers resource, skipping gateway spawn");
                return;
            }

            Vector3i portalPos = null;
            for (BlockMapMarkersResource.BlockMapMarkerData marker : markers.getMarkers().values()) {
                if (marker.getName() != null &&
                        marker.getName().contains("Forgotten_Temple_Portal_Enter")) {
                    portalPos = marker.getPosition();
                    break;
                }
            }

            if (portalPos == null) {
                getLogger().atWarning().log("Forgotten Temple portal not found in world markers");
                return;
            }

            final Vector3i portal = portalPos;

            // Set spawn a few blocks east of the portal, facing west toward it
            WorldConfig config = world.getWorldConfig();
            Transform spawn = new Transform(
                    portal.getX() + 5.0, portal.getY(), portal.getZ() + 0.5,
                    0.0f, -90.0f, 0.0f);
            config.setSpawnProvider(new GlobalSpawnProvider(spawn));
            config.markChanged();

            getLogger().atInfo().log("Gateway spawn set near portal at (%d, %d, %d)",
                    portal.getX(), portal.getY(), portal.getZ());

            // Place spawn suppression zone around the portal to clear vanilla NPCs
            world.execute(() -> {
                try {
                    SpawnSuppressionController controller = world.getEntityStore().getStore().getResource(
                            SpawnSuppressionController.getResourceType());
                    if (controller == null) {
                        getLogger().atWarning().log("No SpawnSuppressionController, skipping");
                        return;
                    }

                    if (!controller.getSpawnSuppressorMap().containsKey(GATEWAY_SUPPRESSION_UUID)) {
                        Vector3d suppressionPos = new Vector3d(
                                portal.getX(), portal.getY(), portal.getZ());
                        controller.getSpawnSuppressorMap().put(
                                GATEWAY_SUPPRESSION_UUID,
                                new SpawnSuppressorEntry("Nat20_Gateway", suppressionPos));
                        getLogger().atInfo().log("Gateway suppression zone placed");
                    }
                } catch (Exception e) {
                    getLogger().atSevere().withCause(e).log("Failed to place gateway suppression");
                }
            });

        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("Failed to configure gateway spawn");
        }
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("Natural 20 shutting down...");
    }
}
