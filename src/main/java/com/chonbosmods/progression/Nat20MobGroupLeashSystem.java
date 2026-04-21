package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.mob.Nat20MobGroupMemberComponent;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.chonbosmods.quest.poi.SlotRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps ambient/POI mob group champions tethered to their boss by teleport-recalling
 * strays, but only when it's invisible to players and the mob isn't still in combat.
 *
 * <p>Design constraints driving this system:
 * <ul>
 *   <li>The Hytale SDK exposes no role-agnostic "walk to point" API. {@code setLeashPoint}
 *       is a soft nudge that only works if the role JSON defines a {@code ReturnToPost}
 *       state with {@code SensorLeash} + {@code BodyMotionSeek} (our {@code Guard.json}
 *       pattern). 80+ base-game mob roles don't have it, and editing each is not on the
 *       table. The only universal movement primitive is
 *       {@link TransformComponent#teleportPosition}. So we teleport.</li>
 *   <li>Teleport must never happen while a player can see it. We gate on a
 *       {@link #PLAYER_PROXIMITY_RADIUS} check, not on behavior-tree target slots,
 *       because target refs invalidate when the player leaves the chunk.</li>
 *   <li>Teleport must never happen during combat, even when the player has just run out
 *       of view. We use {@code lastCombatMillis} on {@link Nat20MobGroupMemberComponent},
 *       written by {@link Nat20MobGroupCombatStampSystem} on every damage event. This is
 *       a persisted timestamp keyed to the entity, not a live-ref check, so chunk reload
 *       and server restart don't reset it.</li>
 *   <li>Teleport requires {@link #REQUIRED_OUT_OF_RANGE_TICKS} consecutive out-of-range
 *       ticks before firing. The counter is also on the member component so a chunk flap
 *       doesn't reset progress.</li>
 * </ul>
 *
 * <p>Per-check debounce (next-tick time) stays in an ephemeral
 * {@link ConcurrentHashMap}: losing it on chunk reload just means the next tick fires
 * one frame earlier, which is correctness-neutral.
 */
public class Nat20MobGroupLeashSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobGroupLeash");
    private static final Query<EntityStore> QUERY = Query.any();

    /** Past this range, the champion is considered strayed and enters the gate sequence. */
    private static final double TELEPORT_RADIUS = 25.0;
    private static final double TELEPORT_RADIUS_SQ = TELEPORT_RADIUS * TELEPORT_RADIUS;

    /** No teleport fires if any player is within this radius of the champion. */
    private static final double PLAYER_PROXIMITY_RADIUS = 40.0;

    /** A mob counts as "in combat" for this long after any damage it deals or takes. */
    private static final long COMBAT_GRACE_MS = 15_000L;

    /** How many consecutive out-of-range ticks must pass before teleport fires. */
    private static final int REQUIRED_OUT_OF_RANGE_TICKS = 3;

    /** How often each champion is re-evaluated. */
    private static final long TICK_INTERVAL_MS = 2_000L;

    /** Scatter radius when teleporting so multiple champions don't stack on the boss. */
    private static final double TELEPORT_SCATTER = 2.0;

    private final Nat20MobGroupRegistry registry;
    private final ConcurrentHashMap<Ref<EntityStore>, Long> nextCheckAt = new ConcurrentHashMap<>();
    private final Random rng = new Random();

    public Nat20MobGroupLeashSystem(Nat20MobGroupRegistry registry) {
        this.registry = registry;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() { return QUERY; }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (!ref.isValid()) {
            nextCheckAt.remove(ref);
            return;
        }

        Nat20MobGroupMemberComponent member =
                store.getComponent(ref, Natural20.getMobGroupMemberType());
        if (member == null) return;

        long now = System.currentTimeMillis();
        Long next = nextCheckAt.get(ref);
        if (next != null && now < next) return;
        nextCheckAt.put(ref, now + TICK_INTERVAL_MS);

        MobGroupRecord record = registry.get(member.getGroupKey());
        if (record == null) return;

        SlotRecord bossSlot = findLiveBoss(record);
        if (bossSlot == null) return;
        if (member.getSlotIndex() == bossSlot.getSlotIndex()) return;

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        UUID bossUuid;
        try {
            bossUuid = UUID.fromString(bossSlot.getCurrentUuid());
        } catch (IllegalArgumentException e) {
            return;
        }
        Ref<EntityStore> bossRef = world.getEntityRef(bossUuid);
        if (bossRef == null || !bossRef.isValid()) return;

        TransformComponent champT =
                store.getComponent(ref, TransformComponent.getComponentType());
        TransformComponent bossT =
                store.getComponent(bossRef, TransformComponent.getComponentType());
        if (champT == null || bossT == null) return;

        Vector3d champPos = champT.getPosition();
        Vector3d bossPos = bossT.getPosition();
        if (champPos == null || bossPos == null) return;

        double dx = bossPos.getX() - champPos.getX();
        double dy = bossPos.getY() - champPos.getY();
        double dz = bossPos.getZ() - champPos.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= TELEPORT_RADIUS_SQ) {
            if (member.getOutOfRangeTicks() != 0) member.setOutOfRangeTicks(0);
            return;
        }

        // Combat grace: any damage involving this mob within the last 15s suppresses enforcement.
        // Counter is not reset here — if combat ends while still out of range, we resume the
        // countdown from where it stopped rather than starting over.
        if (now - member.getLastCombatMillis() < COMBAT_GRACE_MS) {
            return;
        }

        // Player proximity gate: no teleport if any player can see the champion. Resets
        // the out-of-range counter so a player drifting in and out doesn't slowly march
        // the mob toward a teleport.
        if (anyPlayerNearby(champPos, store)) {
            if (member.getOutOfRangeTicks() != 0) member.setOutOfRangeTicks(0);
            return;
        }

        int ticks = member.getOutOfRangeTicks() + 1;
        if (ticks < REQUIRED_OUT_OF_RANGE_TICKS) {
            member.setOutOfRangeTicks(ticks);
            return;
        }

        Vector3d target = scatterAround(bossPos, TELEPORT_SCATTER);
        champT.teleportPosition(target);
        member.setOutOfRangeTicks(0);
        LOGGER.atFine().log(
                "Leash teleport: group=%s slot=%d distSq=%.0f to (%.1f,%.1f,%.1f)",
                member.getGroupKey(), member.getSlotIndex(), distSq,
                target.getX(), target.getY(), target.getZ());
    }

    private boolean anyPlayerNearby(Vector3d champPos, Store<EntityStore> store) {
        List<Ref<EntityStore>> nearby =
                TargetUtil.getAllEntitiesInSphere(champPos, PLAYER_PROXIMITY_RADIUS, store);
        for (Ref<EntityStore> r : nearby) {
            if (r == null || !r.isValid()) continue;
            if (store.getComponent(r, Natural20.getPlayerDataType()) != null) return true;
        }
        return false;
    }

    private static SlotRecord findLiveBoss(MobGroupRecord record) {
        for (SlotRecord s : record.getSlots()) {
            if (s.isBoss() && !s.isDead() && s.getCurrentUuid() != null) return s;
        }
        return null;
    }

    private Vector3d scatterAround(Vector3d center, double radius) {
        double dx = (rng.nextDouble() - 0.5) * 2 * radius;
        double dz = (rng.nextDouble() - 0.5) * 2 * radius;
        return new Vector3d(center.getX() + dx, center.getY(), center.getZ() + dz);
    }
}
