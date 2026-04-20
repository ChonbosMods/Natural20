package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.mob.Nat20MobGroupMemberComponent;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.chonbosmods.quest.poi.SlotRecord;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.UUID;

/**
 * Prevents duplicate mob-group entities when native Hytale persistence revives an entity
 * after the {@link com.chonbosmods.quest.poi.MobGroupChunkListener} has already respawned
 * a fresh one for the same slot.
 *
 * <p>Race we're handling: player teleports (or portals) to an area whose chunks load in a
 * burst. The reconcile listener fires, sphere-scan misses because native persistence
 * hasn't committed the revived entities yet, UUID-check fails (the saved UUID isn't in
 * the world yet), so we respawn. Then native persistence completes, reviving the old
 * entity with its saved UUID and preserved {@link Nat20MobGroupMemberComponent}. Now two
 * entities claim the same {@code (groupKey, slotIndex)} and the player sees duplicates.
 *
 * <p>Detection: this system's {@code onEntityAdded} fires for every entity add, including
 * native revivals. Revivals arrive WITH their persisted component set attached, so we can
 * test for {@code Nat20MobGroupMemberComponent} on the added ref. Freshly-spawned mobs
 * get their member component attached AFTER {@code NPCPlugin.spawnEntity} returns (in
 * {@code respawnSlotInternal}), so the component isn't present at add time - this system
 * correctly skips them.
 *
 * <p>Resolution: if the added entity's UUID matches the record's {@code slot.currentUuid},
 * this IS the authoritative entity (e.g., normal revival when no respawn happened) - keep.
 * Otherwise it's a late revival arriving after a respawn already completed - remove it.
 * If the group record is gone from the registry (despawned/decayed), also remove.
 */
public class Nat20MobGroupDedupSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobGroupDedup");
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20MobGroupRegistry registry;

    public Nat20MobGroupDedupSystem(Nat20MobGroupRegistry registry) {
        this.registry = registry;
    }

    @Override public Query<EntityStore> getQuery() { return QUERY; }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason,
                               Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        // No cleanup needed: member component state is persisted with the entity and the
        // group record's slot.currentUuid is updated by explicit kill/decay paths, not here.
    }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason,
                              Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        Nat20MobGroupMemberComponent member =
                store.getComponent(ref, Natural20.getMobGroupMemberType());
        if (member == null) return;

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return;
        UUID entityUuid = npc.getUuid();
        if (entityUuid == null) return;

        MobGroupRecord record = registry.get(member.getGroupKey());
        if (record == null) {
            // Group was removed (ambient decay, POI completion); the revival is an orphan.
            scheduleRemoval(ref, "orphan revival",
                    member.getGroupKey(), member.getSlotIndex(), entityUuid, null);
            return;
        }

        for (SlotRecord slot : record.getSlots()) {
            if (slot.getSlotIndex() != member.getSlotIndex()) continue;

            // Dead slots shouldn't have revivals - if they do, it's a stale persisted
            // entity from before the slot was killed. Remove.
            if (slot.isDead()) {
                scheduleRemoval(ref, "dead-slot revival",
                        member.getGroupKey(), slot.getSlotIndex(), entityUuid, null);
                return;
            }

            String expectedUuid = slot.getCurrentUuid();
            if (expectedUuid == null) {
                // No authoritative UUID yet (slot was cleared or never tracked).
                // First arrival wins: adopt this revival as the canonical entity.
                slot.setCurrentUuid(entityUuid.toString());
                registry.saveAsync();
                return;
            }

            if (entityUuid.toString().equals(expectedUuid)) return; // canonical; keep

            // Different UUID than the authoritative one: duplicate revival. Remove.
            scheduleRemoval(ref, "duplicate revival",
                    member.getGroupKey(), slot.getSlotIndex(), entityUuid, expectedUuid);
            return;
        }
    }

    /**
     * Defer the actual entity removal to the next world-thread pass. Doing the removal
     * synchronously from {@code onEntityAdded} (even via {@code CommandBuffer.tryRemoveEntity})
     * risks triggering Hytale's EntityChunkLoadingSystem.onComponentRemoved which in turn
     * calls Store.addEntities; if that cascades during the current tick the store throws
     * "Store is currently processing". Scheduling via {@code world.execute} runs the
     * removal between ticks, which is safe.
     */
    private void scheduleRemoval(Ref<EntityStore> ref, String reasonLabel,
                                 String groupKey, int slotIndex,
                                 UUID entityUuid, String canonicalUuid) {
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return; // nothing to schedule on yet
        world.execute(() -> {
            try {
                if (!ref.isValid()) return;
                Store<EntityStore> s = world.getEntityStore().getStore();
                s.removeEntity(ref, RemoveReason.REMOVE);
                if (canonicalUuid != null) {
                    LOGGER.atInfo().log(
                            "Removed %s: %s slot=%d revivalUuid=%s canonicalUuid=%s",
                            reasonLabel, groupKey, slotIndex, entityUuid, canonicalUuid);
                } else {
                    LOGGER.atInfo().log("Removed %s: %s slot=%d uuid=%s",
                            reasonLabel, groupKey, slotIndex, entityUuid);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Dedup removal failed: %s slot=%d",
                        groupKey, slotIndex);
            }
        });
    }
}
