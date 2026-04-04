package com.chonbosmods.marker;

import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20NpcData.QuestMarkerState;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages floating quest marker hologram entities above NPCs.
 * Uses the Projectile marker + Nameplate pattern: an invisible projectile entity shell
 * with a Nameplate component renders floating text ("!" or "?") above NPC heads.
 *
 * Position updates run on a 1Hz tick via the POI proximity executor.
 */
public class QuestMarkerManager {

    public static final QuestMarkerManager INSTANCE = new QuestMarkerManager();

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|QuestMarker");
    private static final float MARKER_Y_OFFSET = 2.8f;

    /** NPC entity UUID → marker entity UUID */
    private final Map<UUID, UUID> npcToMarker = new ConcurrentHashMap<>();

    private QuestMarkerManager() {}

    /**
     * Create, update, or remove the floating marker above an NPC based on quest state.
     * Must be called from the world thread.
     */
    public void syncMarker(World world, Store<EntityStore> store,
                           Ref<EntityStore> npcRef, UUID npcUuid,
                           Nat20NpcData npcData) {
        QuestMarkerState state = npcData.getQuestMarkerState();
        UUID existingMarkerUuid = npcToMarker.get(npcUuid);

        if (state == QuestMarkerState.NONE) {
            if (existingMarkerUuid != null) {
                removeMarkerEntity(world, store, existingMarkerUuid);
                npcToMarker.remove(npcUuid);
            }
            return;
        }

        String markerText = state == QuestMarkerState.QUEST_AVAILABLE ? "!" : "?";

        if (existingMarkerUuid != null) {
            Ref<EntityStore> markerRef = world.getEntityRef(existingMarkerUuid);
            if (markerRef != null) {
                store.putComponent(markerRef, Nameplate.getComponentType(),
                    new Nameplate(markerText));
                updateMarkerPosition(store, npcRef, markerRef);
                return;
            }
            // Marker entity lost (chunk unload etc.), will recreate below
            npcToMarker.remove(npcUuid);
        }

        spawnMarkerEntity(world, store, npcRef, npcUuid, markerText);
    }

    /**
     * Update positions of all marker entities to follow their NPCs.
     * Call periodically from the world thread (~1Hz).
     */
    public void tickPositions(World world) {
        if (npcToMarker.isEmpty()) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        Iterator<Map.Entry<UUID, UUID>> it = npcToMarker.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            UUID npcUuid = entry.getKey();
            UUID markerUuid = entry.getValue();

            Ref<EntityStore> npcRef = world.getEntityRef(npcUuid);
            Ref<EntityStore> markerRef = world.getEntityRef(markerUuid);

            if (npcRef == null || markerRef == null) {
                if (markerRef != null) {
                    removeMarkerEntity(world, store, markerUuid);
                }
                it.remove();
                continue;
            }

            updateMarkerPosition(store, npcRef, markerRef);
        }
    }

    /**
     * Remove all marker entities. Call on shutdown.
     */
    public void removeAllMarkers(World world) {
        if (world == null || npcToMarker.isEmpty()) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID markerUuid : npcToMarker.values()) {
            removeMarkerEntity(world, store, markerUuid);
        }
        npcToMarker.clear();
    }

    private void spawnMarkerEntity(World world, Store<EntityStore> store,
                                    Ref<EntityStore> npcRef, UUID npcUuid,
                                    String text) {
        TransformComponent npcTransform = store.getComponent(npcRef,
            TransformComponent.getComponentType());
        if (npcTransform == null) {
            LOGGER.atWarning().log("Cannot spawn marker: NPC %s has no TransformComponent", npcUuid);
            return;
        }

        Vector3d npcPos = npcTransform.getPosition();
        Vector3d markerPos = new Vector3d(
            npcPos.getX(), npcPos.getY() + MARKER_Y_OFFSET, npcPos.getZ());

        UUID markerUuid = UUID.randomUUID();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        // Projectile marker: tags entity as projectile type for client rendering
        holder.addComponent(Projectile.getComponentType(), Projectile.INSTANCE);

        // Prop marker: ensures client recognizes entity as renderable
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());

        // Position above NPC head
        holder.addComponent(TransformComponent.getComponentType(),
            new TransformComponent(markerPos, new Vector3f(0, 0, 0)));

        // UUID for entity tracking
        holder.addComponent(UUIDComponent.getComponentType(),
            new UUIDComponent(markerUuid));

        // Network sync for client visibility
        holder.addComponent(NetworkId.getComponentType(),
            new NetworkId(store.getExternalData().takeNextNetworkId()));

        // Floating text
        holder.addComponent(Nameplate.getComponentType(), new Nameplate(text));

        // Non-interactable
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);

        store.addEntity(holder, AddReason.SPAWN);
        npcToMarker.put(npcUuid, markerUuid);

        LOGGER.atInfo().log("Spawned quest marker '%s' above NPC %s at %.0f, %.1f, %.0f",
            text, npcUuid, markerPos.getX(), markerPos.getY(), markerPos.getZ());
    }

    private void removeMarkerEntity(World world, Store<EntityStore> store, UUID markerUuid) {
        Ref<EntityStore> markerRef = world.getEntityRef(markerUuid);
        if (markerRef != null) {
            store.removeEntity(markerRef, RemoveReason.REMOVE);
            LOGGER.atFine().log("Removed marker entity %s", markerUuid);
        }
    }

    private void updateMarkerPosition(Store<EntityStore> store,
                                       Ref<EntityStore> npcRef,
                                       Ref<EntityStore> markerRef) {
        TransformComponent npcTransform = store.getComponent(npcRef,
            TransformComponent.getComponentType());
        if (npcTransform == null) return;

        Vector3d npcPos = npcTransform.getPosition();
        store.putComponent(markerRef, TransformComponent.getComponentType(),
            new TransformComponent(
                new Vector3d(npcPos.getX(), npcPos.getY() + MARKER_Y_OFFSET, npcPos.getZ()),
                new Vector3f(0, 0, 0)));
    }
}
