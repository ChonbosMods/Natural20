package com.chonbosmods.quest.poi;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.mob.Nat20MobGroupMemberComponent;
import com.chonbosmods.progression.Nat20MobGroupSpawner;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reconciliation listener for persistent POI mob groups. Invoked from the plugin's
 * {@code ChunkPreLoadProcessEvent} handler; defers work to {@code world.execute(...)}
 * so native entity persistence has finished before we scan the store.
 *
 * <p>For every record whose anchor falls inside the loaded chunk (with 1-chunk buffer),
 * runs the 6-step flow from the design doc's §5 per {@code !isDead} slot:
 * member-scan → UUID-check → per-group lock → debounce → spawn → post-spawn sweep.
 *
 * <p>See {@code docs/plans/2026-04-16-poi-quest-group-spawn-integration-design.md}.
 */
public class MobGroupChunkListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobGroupReconcile");

    /** Hytale chunks are 32x32 blocks. */
    private static final int CHUNK_BLOCK_SIZE = 32;

    /** 3x3-chunk radius around anchor to scan for members (members spread ±3 blocks from anchor). */
    private static final double MEMBER_SCAN_RADIUS = 1.5 * CHUNK_BLOCK_SIZE;

    /** Debounce per-group to absorb the burst of 9 chunk-load events around an anchor. */
    private static final long CHECK_COOLDOWN_MS = 500L;

    private final Nat20MobGroupRegistry registry;
    private final Nat20MobGroupSpawner spawner;

    /** Per-group reconcile lock: prevents overlapping chunk-loads racing to spawn the same slot. */
    private final ConcurrentHashMap<String, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

    /** Per-group last-check timestamp for the debounce. */
    private final ConcurrentHashMap<String, Long> lastCheck = new ConcurrentHashMap<>();

    public MobGroupChunkListener(Nat20MobGroupRegistry registry, Nat20MobGroupSpawner spawner) {
        this.registry = registry;
        this.spawner = spawner;
    }

    /** Called by the plugin's chunk-load handler; chunkBlockX/Z are block coordinates. */
    public void onChunkLoad(World world, int chunkBlockX, int chunkBlockZ) {
        int loadedChunkX = Math.floorDiv(chunkBlockX, CHUNK_BLOCK_SIZE);
        int loadedChunkZ = Math.floorDiv(chunkBlockZ, CHUNK_BLOCK_SIZE);

        for (MobGroupRecord record : registry.all()) {
            int anchorChunkX = (int) Math.floor(record.getAnchorX() / CHUNK_BLOCK_SIZE);
            int anchorChunkZ = (int) Math.floor(record.getAnchorZ() / CHUNK_BLOCK_SIZE);

            // 1-chunk buffer: trigger on any of the 9 chunks around the anchor loading.
            if (Math.abs(anchorChunkX - loadedChunkX) > 1) continue;
            if (Math.abs(anchorChunkZ - loadedChunkZ) > 1) continue;

            reconcile(world, record);
        }
    }

    /**
     * Run {@code task} while holding the per-group lock. Used by ambient decay sweep to
     * serialize despawn + registry.remove against chunk-load reconciliation. Skips the
     * task entirely if the lock is already held: caller must tolerate being no-op'd
     * (decay sweep retries next tick).
     */
    public void withGroupLock(String groupKey, Runnable task) {
        AtomicBoolean lock = inFlight.computeIfAbsent(groupKey, k -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) return;
        try {
            task.run();
        } finally {
            lock.set(false);
        }
    }

    private void reconcile(World world, MobGroupRecord record) {
        String groupKey = record.getGroupKey();

        // Anchor-chunk-loaded gate. Mirrors SettlementWorldGenListener: reconciliation
        // must not run before the anchor's chunk has finished loading, or the sphere-scan
        // will miss natively-reviving entities and we'll spawn duplicates on top of them.
        // The listener fires on every chunk in the vicinity; we wait for the one that
        // contains the anchor itself to be fully loaded.
        int anchorChunkX = (int) Math.floor(record.getAnchorX() / CHUNK_BLOCK_SIZE);
        int anchorChunkZ = (int) Math.floor(record.getAnchorZ() / CHUNK_BLOCK_SIZE);
        long anchorChunkKey = ((long) anchorChunkX << 32) | (anchorChunkZ & 0xFFFFFFFFL);
        if (world.getChunkIfLoaded(anchorChunkKey) == null) {
            return;
        }

        // Debounce
        long now = System.currentTimeMillis();
        Long last = lastCheck.get(groupKey);
        if (last != null && now - last < CHECK_COOLDOWN_MS) return;
        lastCheck.put(groupKey, now);

        // Per-group lock
        AtomicBoolean lock = inFlight.computeIfAbsent(groupKey, k -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) return;

        world.execute(() -> {
            try {
                runReconcilePass(world, record);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Reconcile failed for %s", groupKey);
            } finally {
                lock.set(false);
            }
        });
    }

    private void runReconcilePass(World world, MobGroupRecord record) {
        // Race-tolerance: the ambient decay sweep may have removed this record between
        // the lock-grab in reconcile() and this deferred execute(). If so, don't respawn
        // slots on a record that's already despawned and deleted.
        if (registry.get(record.getGroupKey()) == null) {
            LOGGER.atFine().log("runReconcilePass: record %s no longer in registry, skipping",
                    record.getGroupKey());
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        // Member-scan: build a (groupKey, slotIndex) → Ref map from live entities around the anchor.
        Vector3d anchor = new Vector3d(record.getAnchorX(), record.getAnchorY(), record.getAnchorZ());
        Map<Integer, List<Ref<EntityStore>>> liveBySlot = new HashMap<>();
        List<Ref<EntityStore>> nearby;
        try {
            nearby = TargetUtil.getAllEntitiesInSphere(anchor, MEMBER_SCAN_RADIUS, store);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Entity sweep failed for %s", record.getGroupKey());
            return;
        }
        // Tier-2 naked-revival sweep: native persistence sometimes revives our
        // group mobs without the custom Nat20MobGroupMemberComponent attached. They
        // become invisible to the (groupKey, slotIndex) scan below and to the kill
        // tracker, but still exist in the world and still carry Tier.BOSS / CHAMPION
        // scale tags (so they still drop loot and award XP). Without this sweep,
        // reconciliation would spawn a fresh mob on top of each naked revival and
        // the player would see duplicates (e.g. two bosses, two sets of champions).
        //
        // Detection heuristic: same mobRole as the record AND missing our member
        // component. Wild same-role spawns near a dungeon anchor are rare enough
        // that the false-positive cost is acceptable vs. duplicate-boss fights.
        int ghostsRemoved = 0;
        String expectedRole = record.getMobRole();
        for (Ref<EntityStore> ref : nearby) {
            if (!ref.isValid()) continue;
            if (store.getComponent(ref, Natural20.getMobGroupMemberType()) != null) continue;
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc == null) continue;
            if (!expectedRole.equals(npc.getRoleName())) continue;
            try {
                store.removeEntity(ref, RemoveReason.REMOVE);
                ghostsRemoved++;
            } catch (Exception ignored) {}
        }
        if (ghostsRemoved > 0) {
            LOGGER.atInfo().log("Ghost-revival sweep: removed %d naked %s entities near %s",
                    ghostsRemoved, expectedRole, record.getGroupKey());
        }

        // Primary member-scan: map component-tagged entities by slot.
        for (Ref<EntityStore> ref : nearby) {
            if (!ref.isValid()) continue;
            Nat20MobGroupMemberComponent member =
                    store.getComponent(ref, Natural20.getMobGroupMemberType());
            if (member == null) continue;
            if (!record.getGroupKey().equals(member.getGroupKey())) continue;
            liveBySlot.computeIfAbsent(member.getSlotIndex(), k -> new ArrayList<>()).add(ref);
        }

        int respawned = 0;
        int matched = 0;
        int sweptDuplicates = ghostsRemoved;

        for (SlotRecord slot : record.getSlots()) {
            if (slot.isDead()) continue;

            List<Ref<EntityStore>> matches = liveBySlot.get(slot.getSlotIndex());

            if (matches != null && !matches.isEmpty()) {
                // Step 1: member-scan hit. Keep the first, despawn duplicates.
                Ref<EntityStore> keep = matches.get(0);
                UUID keepUuid = safeUuid(store, keep);
                if (keepUuid != null) slot.setCurrentUuid(keepUuid.toString());
                for (int i = 1; i < matches.size(); i++) {
                    try {
                        store.removeEntity(matches.get(i), RemoveReason.REMOVE);
                        sweptDuplicates++;
                    } catch (Exception ignored) {}
                }
                matched++;
                continue;
            }

            // Step 2: UUID-check as confirmation. Accept only if the resolved entity
            // also carries a matching member component.
            String currentUuidStr = slot.getCurrentUuid();
            if (currentUuidStr != null) {
                try {
                    UUID uuid = UUID.fromString(currentUuidStr);
                    Ref<EntityStore> ref = world.getEntityRef(uuid);
                    if (ref != null) {
                        Nat20MobGroupMemberComponent member =
                                store.getComponent(ref, Natural20.getMobGroupMemberType());
                        if (member != null
                                && record.getGroupKey().equals(member.getGroupKey())
                                && member.getSlotIndex() == slot.getSlotIndex()) {
                            matched++;
                            continue;
                        }
                    }
                } catch (Exception ignored) {}
                // Stale UUID: clear and fall through to spawn.
                slot.setCurrentUuid(null);
            }

            // Step 5: spawn. Lock + debounce already held at reconcile() entry.
            UUID newUuid = spawner.respawnSlot(world, record, slot);
            if (newUuid != null) {
                respawned++;
                // Step 6: post-spawn sweep. If another entity already bears this
                // (groupKey, slotIndex) somehow (e.g. native persistence revived in parallel),
                // despawn the new spawn to avoid duplicates.
                List<Ref<EntityStore>> postScan;
                try {
                    postScan = TargetUtil.getAllEntitiesInSphere(anchor, MEMBER_SCAN_RADIUS, store);
                } catch (Exception e) {
                    continue;
                }
                List<Ref<EntityStore>> sameSlot = new ArrayList<>();
                for (Ref<EntityStore> ref : postScan) {
                    if (!ref.isValid()) continue;
                    Nat20MobGroupMemberComponent m =
                            store.getComponent(ref, Natural20.getMobGroupMemberType());
                    if (m == null) continue;
                    if (!record.getGroupKey().equals(m.getGroupKey())) continue;
                    if (m.getSlotIndex() != slot.getSlotIndex()) continue;
                    sameSlot.add(ref);
                }
                if (sameSlot.size() > 1) {
                    String keepUuidStr = slot.getCurrentUuid();
                    for (Ref<EntityStore> ref : sameSlot) {
                        UUID u = safeUuid(store, ref);
                        if (u != null && u.toString().equals(keepUuidStr)) continue;
                        try {
                            store.removeEntity(ref, RemoveReason.REMOVE);
                            sweptDuplicates++;
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        if (respawned > 0 || sweptDuplicates > 0) {
            registry.saveAsync();
            LOGGER.atFine().log("Reconcile %s: matched=%d respawned=%d sweptDuplicates=%d",
                    record.getGroupKey(), matched, respawned, sweptDuplicates);
        }
    }

    private static UUID safeUuid(Store<EntityStore> store, Ref<EntityStore> ref) {
        try {
            var npc = store.getComponent(ref,
                    com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
            return npc != null ? npc.getUuid() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
