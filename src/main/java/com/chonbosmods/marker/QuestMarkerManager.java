package com.chonbosmods.marker;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.marker.QuestMarkerComponent.MarkerType;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestStateManager;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for quest marker entities that float above NPCs.
 *
 * <p>Markers are dropped-item entities with deterministic UUIDs, positioned
 * at a fixed offset above the NPC. They use the same entity composition pattern
 * as the NPC Quest Icons mod: intangible, invulnerable, non-merging items with
 * infinite pickup delay.
 *
 * <p>All spawn/despawn/reposition methods require a {@link CommandBuffer} and must
 * be called from within an ECS tick context (e.g., from a tick system).
 */
public class QuestMarkerManager {

    public static final QuestMarkerManager INSTANCE = new QuestMarkerManager();

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Markers");

    static final double Y_OFFSET = 3.0;
    static final float MARKER_SCALE = 1.5f;
    static final String ITEM_QUEST_AVAILABLE = "Nat20_QuestAvailable";
    static final String ITEM_QUEST_TURN_IN = "Nat20_QuestTurnIn";

    /** Active markers keyed by NPC UUID, each NPC can have one marker per type. */
    private final Map<UUID, EnumMap<MarkerType, Ref<EntityStore>>> activeMarkers = new HashMap<>();

    /** Thread-safe queue of NPC UUIDs that need marker recalculation. */
    private final Set<UUID> pendingRecalculations = ConcurrentHashMap.newKeySet();

    private QuestMarkerManager() {}

    /**
     * Spawn a single marker entity above an NPC. If a marker of this type already
     * exists for this NPC (in our tracking map or recoverable by deterministic UUID),
     * the existing marker is repositioned instead of creating a duplicate.
     *
     * <p>Must be called from an ECS tick context with a valid CommandBuffer.
     *
     * @param store         the entity store
     * @param commandBuffer the command buffer for entity creation
     * @param npcUuid       the UUID of the NPC to place the marker above
     * @param type          the marker type (quest available or turn-in)
     * @param npcPos        the current position of the NPC
     */
    public void spawnMarker(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                            UUID npcUuid, MarkerType type, Vector3d npcPos) {
        // Check if we already have this marker tracked
        EnumMap<MarkerType, Ref<EntityStore>> npcMarkers = activeMarkers.get(npcUuid);
        if (npcMarkers != null && npcMarkers.containsKey(type)) {
            Ref<EntityStore> existingRef = npcMarkers.get(type);
            // Verify it still exists in the store, reposition if so
            TransformComponent tc = store.getComponent(existingRef, TransformComponent.getComponentType());
            if (tc != null) {
                repositionSingle(commandBuffer, existingRef, npcPos);
                return;
            }
            // Ref is stale: remove from tracking and fall through to respawn
            npcMarkers.remove(type);
        }

        // Check if a marker entity with our deterministic UUID already exists
        // (e.g., recovered after server restart)
        UUID markerUuid = deterministicUuid(npcUuid, type);
        EntityStore entityStore = (EntityStore) store.getExternalData();
        Ref<EntityStore> existingRef = entityStore.getRefFromUUID(markerUuid);
        if (existingRef != null) {
            repositionSingle(commandBuffer, existingRef, npcPos);
            trackMarker(npcUuid, type, existingRef);
            return;
        }

        // Create new marker entity
        try {
            int networkId = entityStore.takeNextNetworkId();
            Vector3d markerPos = new Vector3d(npcPos.x, npcPos.y + Y_OFFSET, npcPos.z);

            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(UUIDComponent.getComponentType(),
                    new UUIDComponent(markerUuid));
            holder.addComponent(TransformComponent.getComponentType(),
                    new TransformComponent(markerPos, new Vector3f(0.0f, 0.0f, 0.0f)));
            holder.addComponent(ItemComponent.getComponentType(),
                    makeItemComponent(type));
            holder.addComponent(EntityScaleComponent.getComponentType(),
                    new EntityScaleComponent(MARKER_SCALE));
            holder.addComponent(Intangible.getComponentType(),
                    Intangible.INSTANCE);
            holder.addComponent(Invulnerable.getComponentType(),
                    Invulnerable.INSTANCE);
            holder.addComponent(PreventItemMerging.getComponentType(),
                    PreventItemMerging.INSTANCE);
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(networkId));
            holder.addComponent(QuestMarkerComponent.getComponentType(),
                    new QuestMarkerComponent(npcUuid, type));

            commandBuffer.addEntity(holder, AddReason.SPAWN);

            // We can't get the Ref from the holder before the command buffer flushes,
            // so recover it on the next tick via deterministic UUID lookup.
            // For now, store null and let the next spawnMarker/reposition call recover it.
            LOGGER.atFine().log("Spawned %s marker for NPC %s", type, npcUuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn %s marker for NPC %s", type, npcUuid);
        }
    }

    /**
     * Remove a specific marker type from an NPC. If the marker entity is not found
     * in the tracking map, attempts recovery via deterministic UUID.
     *
     * <p>Must be called from an ECS tick context with a valid CommandBuffer.
     *
     * @param store         the entity store
     * @param commandBuffer the command buffer for entity removal
     * @param npcUuid       the UUID of the NPC whose marker to remove
     * @param type          the marker type to remove
     */
    public void despawnMarker(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                              UUID npcUuid, MarkerType type) {
        Ref<EntityStore> ref = removeTracked(npcUuid, type);
        if (ref == null) {
            // Try deterministic UUID recovery
            EntityStore entityStore = (EntityStore) store.getExternalData();
            ref = entityStore.getRefFromUUID(deterministicUuid(npcUuid, type));
        }
        if (ref != null) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            LOGGER.atFine().log("Despawned %s marker for NPC %s", type, npcUuid);
        }
    }

    /**
     * Remove all markers for an NPC.
     *
     * <p>Must be called from an ECS tick context with a valid CommandBuffer.
     *
     * @param store         the entity store
     * @param commandBuffer the command buffer for entity removal
     * @param npcUuid       the UUID of the NPC whose markers to remove
     */
    public void despawnAllMarkers(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                                  UUID npcUuid) {
        for (MarkerType type : MarkerType.values()) {
            despawnMarker(store, commandBuffer, npcUuid, type);
        }
        activeMarkers.remove(npcUuid);
    }

    /**
     * Update marker positions after an NPC reattaches (e.g., chunk reload).
     * Recovers marker refs from deterministic UUIDs if needed.
     *
     * <p>Must be called from an ECS tick context with a valid CommandBuffer.
     *
     * @param store         the entity store
     * @param commandBuffer the command buffer for component updates
     * @param npcUuid       the UUID of the NPC
     * @param npcPos        the new position of the NPC
     */
    public void repositionMarkers(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                                  UUID npcUuid, Vector3d npcPos) {
        EntityStore entityStore = (EntityStore) store.getExternalData();

        for (MarkerType type : MarkerType.values()) {
            Ref<EntityStore> ref = getTracked(npcUuid, type);
            if (ref == null) {
                // Try deterministic UUID recovery
                ref = entityStore.getRefFromUUID(deterministicUuid(npcUuid, type));
                if (ref != null) {
                    trackMarker(npcUuid, type, ref);
                }
            }
            if (ref != null) {
                // Verify the ref is still valid
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    repositionSingle(commandBuffer, ref, npcPos);
                } else {
                    // Entity gone: clear tracking
                    removeTracked(npcUuid, type);
                }
            }
        }
    }

    /**
     * Check whether a marker of the given type is active for the NPC.
     *
     * @param npcUuid the NPC UUID
     * @param type    the marker type
     * @return true if a marker is tracked (may be stale if entity was removed externally)
     */
    public boolean hasMarker(UUID npcUuid, MarkerType type) {
        EnumMap<MarkerType, Ref<EntityStore>> npcMarkers = activeMarkers.get(npcUuid);
        return npcMarkers != null && npcMarkers.containsKey(type);
    }

    /**
     * Clear all tracking state. Does not despawn entities: use this only when the
     * store is being discarded (e.g., world unload).
     */
    public void clearAll() {
        activeMarkers.clear();
        pendingRecalculations.clear();
    }

    // ---- quest state resolution ----

    /**
     * Determine which marker type a specific player should see for a specific NPC.
     * Returns null if no marker should be shown.
     *
     * <p>Priority order:
     * <ol>
     *   <li>Player has active quest from this NPC with objectives complete: QUEST_TURN_IN</li>
     *   <li>Player has active quest from this NPC (not yet complete): null (hide both)</li>
     *   <li>NPC has a pre-generated quest and player hasn't accepted/completed it: QUEST_AVAILABLE</li>
     *   <li>Otherwise: null</li>
     * </ol>
     *
     * @param playerData the player's persistent data
     * @param npcRecord  the NPC record to check
     * @return the marker type to show, or null for no marker
     */
    public static MarkerType resolveMarkerForPlayer(Nat20PlayerData playerData, NpcRecord npcRecord) {
        QuestStateManager stateManager = Natural20.getInstance().getQuestSystem().getStateManager();
        String npcName = npcRecord.getGeneratedName();

        Map<String, QuestInstance> activeQuests = stateManager.getActiveQuests(playerData);

        // Check active quests from this NPC
        for (QuestInstance quest : activeQuests.values()) {
            if (!npcName.equals(quest.getSourceNpcId())) continue;

            // Priority 1: objectives complete -> turn-in marker
            if ("true".equals(quest.getVariableBindings().get("phase_objectives_complete"))) {
                return MarkerType.QUEST_TURN_IN;
            }

            // Priority 2: active quest in progress -> hide both markers
            return null;
        }

        // Priority 3: NPC has a pre-generated quest the player hasn't accepted or completed
        QuestInstance preGenerated = npcRecord.getPreGeneratedQuest();
        if (preGenerated != null) {
            String questId = preGenerated.getQuestId();
            Set<String> completed = stateManager.getCompletedQuestIds(playerData);
            if (!completed.contains(questId) && !activeQuests.containsKey(questId)) {
                return MarkerType.QUEST_AVAILABLE;
            }
        }

        // (Future) NPC triggered unlockedNewQuest -> QUEST_AVAILABLE

        return null;
    }

    /**
     * Compute the union of needed marker types across all online players for a
     * specific NPC, then spawn missing and despawn unneeded markers.
     *
     * @param store         the entity store
     * @param commandBuffer the command buffer for entity creation/removal
     * @param world         the world (used to iterate online players)
     * @param npcUuid       the NPC's entity UUID
     * @param npcRecord     the NPC record
     * @param npcPos        the NPC's current position
     */
    public void recalculateNpcMarkers(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                                      World world, UUID npcUuid, NpcRecord npcRecord, Vector3d npcPos) {
        EnumSet<MarkerType> needed = EnumSet.noneOf(MarkerType.class);

        // Iterate all online players and union the needed marker types
        @SuppressWarnings("removal")
        var players = world.getPlayers();
        for (Player player : players) {
            @SuppressWarnings("removal")
            UUID playerUuid = player.getPlayerRef().getUuid();
            Ref<EntityStore> playerRef = world.getEntityRef(playerUuid);
            if (playerRef == null) continue;
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            if (playerData == null) continue;

            MarkerType type = resolveMarkerForPlayer(playerData, npcRecord);
            if (type != null) {
                needed.add(type);
            }
        }

        // Spawn missing markers and despawn unneeded ones
        for (MarkerType type : MarkerType.values()) {
            if (needed.contains(type)) {
                spawnMarker(store, commandBuffer, npcUuid, type, npcPos);
            } else {
                despawnMarker(store, commandBuffer, npcUuid, type);
            }
        }
    }

    /**
     * Request a recalculation for a specific NPC. Safe to call from any thread
     * (event handlers, etc.). The actual recalculation will be processed on the
     * next ECS tick by {@link #processPendingRecalculations}.
     *
     * @param npcUuid the NPC's entity UUID
     */
    public void requestRecalculation(UUID npcUuid) {
        pendingRecalculations.add(npcUuid);
    }

    /**
     * Request recalculation for ALL known NPCs across all settlements.
     * Used on player join/leave when any player's quest state affects which
     * markers should be visible globally.
     */
    public void requestFullRecalculation() {
        SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();
        for (SettlementRecord settlement : registry.getAll().values()) {
            for (NpcRecord npc : settlement.getNpcs()) {
                if (npc.getEntityUUID() != null) {
                    pendingRecalculations.add(npc.getEntityUUID());
                }
            }
        }
    }

    /**
     * Process any pending recalculations. Must be called from an ECS tick context
     * with a valid CommandBuffer. Drains the pending queue and recalculates markers
     * for each NPC.
     *
     * @param store         the entity store
     * @param commandBuffer the command buffer
     * @param world         the world (for player iteration)
     */
    public void processPendingRecalculations(Store<EntityStore> store,
                                             CommandBuffer<EntityStore> commandBuffer,
                                             World world) {
        if (pendingRecalculations.isEmpty()) return;

        Set<UUID> batch = new HashSet<>(pendingRecalculations);
        pendingRecalculations.removeAll(batch);

        SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();
        for (UUID npcUuid : batch) {
            NpcRecord npc = registry.getNpcByUUID(npcUuid);
            if (npc == null) {
                despawnAllMarkers(store, commandBuffer, npcUuid);
                continue;
            }
            Vector3d npcPos = new Vector3d(npc.getSpawnX(), npc.getSpawnY(), npc.getSpawnZ());
            recalculateNpcMarkers(store, commandBuffer, world, npcUuid, npc, npcPos);
        }
    }

    // ---- internal helpers ----

    /**
     * Generate a deterministic UUID for a marker entity based on the NPC UUID and type.
     * This allows recovery of marker entities after server restart without persisting refs.
     */
    static UUID deterministicUuid(UUID npcUuid, MarkerType type) {
        String seed = "nat20marker:" + npcUuid + ":" + type.name();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private void trackMarker(UUID npcUuid, MarkerType type, Ref<EntityStore> ref) {
        activeMarkers.computeIfAbsent(npcUuid, k -> new EnumMap<>(MarkerType.class))
                .put(type, ref);
    }

    private Ref<EntityStore> getTracked(UUID npcUuid, MarkerType type) {
        EnumMap<MarkerType, Ref<EntityStore>> npcMarkers = activeMarkers.get(npcUuid);
        return npcMarkers != null ? npcMarkers.get(type) : null;
    }

    private Ref<EntityStore> removeTracked(UUID npcUuid, MarkerType type) {
        EnumMap<MarkerType, Ref<EntityStore>> npcMarkers = activeMarkers.get(npcUuid);
        if (npcMarkers == null) return null;
        Ref<EntityStore> ref = npcMarkers.remove(type);
        if (npcMarkers.isEmpty()) {
            activeMarkers.remove(npcUuid);
        }
        return ref;
    }

    private void repositionSingle(CommandBuffer<EntityStore> commandBuffer,
                                  Ref<EntityStore> ref, Vector3d npcPos) {
        Vector3d markerPos = new Vector3d(npcPos.x, npcPos.y + Y_OFFSET, npcPos.z);
        commandBuffer.putComponent(ref, TransformComponent.getComponentType(),
                new TransformComponent(markerPos, new Vector3f(0.0f, 0.0f, 0.0f)));
    }

    private static ItemComponent makeItemComponent(MarkerType type) {
        String itemId = type == MarkerType.QUEST_AVAILABLE ? ITEM_QUEST_AVAILABLE : ITEM_QUEST_TURN_IN;
        ItemStack stack = new ItemStack(itemId);
        stack.setOverrideDroppedItemAnimation(true);
        ItemComponent comp = new ItemComponent();
        comp.setItemStack(stack);
        comp.setPickupDelay(Float.MAX_VALUE);
        return comp;
    }
}
