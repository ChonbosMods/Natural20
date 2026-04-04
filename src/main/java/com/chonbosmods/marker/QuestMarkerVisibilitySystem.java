package com.chonbosmods.marker;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.marker.QuestMarkerComponent.MarkerType;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player visibility filter for quest marker entities. Runs in the
 * FIND_VISIBLE_ENTITIES_GROUP after CollectVisible, removing markers from
 * a player's visible set when that player should not see them.
 *
 * <p>For example, if a marker entity is QUEST_AVAILABLE but a particular player
 * already has an active quest from that NPC, the marker is hidden from that
 * player (but may remain visible to others who haven't accepted the quest).
 *
 * <p>Also drains the {@link QuestMarkerManager} pending recalculation queue
 * once per tick (on the first player processed).
 */
public class QuestMarkerVisibilitySystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, EntityTrackerSystems.EntityViewer> entityViewerType;
    private final ComponentType<EntityStore, UUIDComponent> uuidComponentType;

    @Nonnull
    private final Query<EntityStore> query;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies;

    /** Tracks whether pending recalculations have been processed this tick. */
    private volatile long lastProcessedTick = -1;
    private long currentTick = 0;

    public QuestMarkerVisibilitySystem(
            ComponentType<EntityStore, EntityTrackerSystems.EntityViewer> entityViewerType,
            ComponentType<EntityStore, UUIDComponent> uuidComponentType) {
        this.entityViewerType = entityViewerType;
        this.uuidComponentType = uuidComponentType;
        @SuppressWarnings("unchecked")
        Query<EntityStore> q = Query.and(new Query[]{entityViewerType, Player.getComponentType(), uuidComponentType});
        this.query = q;
        this.dependencies = Collections.singleton(
                new SystemDependency(Order.AFTER, EntityTrackerSystems.CollectVisible.class));
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // Must be sequential: we process pending recalculations and modify shared state
        return false;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Process pending marker recalculations once per tick (first player triggers it)
        long tick = ++currentTick;
        if (lastProcessedTick != tick) {
            lastProcessedTick = tick;
            EntityStore entityStore = (EntityStore) store.getExternalData();
            QuestMarkerManager.INSTANCE.processPendingRecalculations(
                    store, commandBuffer, entityStore.getWorld());
        }

        // Get the player's viewer and UUID
        EntityTrackerSystems.EntityViewer viewer = chunk.getComponent(index, entityViewerType);
        if (viewer == null) return;

        UUIDComponent uuidComp = chunk.getComponent(index, uuidComponentType);
        if (uuidComp == null) return;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (playerData == null) return;

        SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();

        // Filter visible entities: hide quest markers this player shouldn't see
        Iterator<Ref<EntityStore>> it = viewer.visible.iterator();
        while (it.hasNext()) {
            Ref<EntityStore> ref = it.next();

            // Skip entities that don't have the QuestMarkerComponent
            if (!commandBuffer.getArchetype(ref).contains(QuestMarkerComponent.getComponentType())) {
                continue;
            }

            QuestMarkerComponent marker = store.getComponent(ref, QuestMarkerComponent.getComponentType());
            if (marker == null) continue;

            UUID npcUuid = marker.getNpcUuid();
            NpcRecord npcRecord = registry.getNpcByUUID(npcUuid);
            if (npcRecord == null) {
                // NPC no longer exists: hide marker
                ++viewer.hiddenCount;
                it.remove();
                continue;
            }

            MarkerType expected = QuestMarkerManager.resolveMarkerForPlayer(playerData, npcRecord);
            if (expected != marker.getMarkerType()) {
                // This player should see a different marker (or none): hide this one
                ++viewer.hiddenCount;
                it.remove();
            }
        }
    }
}
