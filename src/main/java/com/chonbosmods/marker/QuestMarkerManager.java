package com.chonbosmods.marker;

import com.chonbosmods.marker.QuestMarkerComponent.MarkerType;
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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
