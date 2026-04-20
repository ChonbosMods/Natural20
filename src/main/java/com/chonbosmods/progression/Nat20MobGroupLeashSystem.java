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
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps ambient/POI mob group champions tethered to their boss when idle.
 *
 * <p>Every {@link #TICK_INTERVAL_MS}, iterates champions (entities with
 * {@link Nat20MobGroupMemberComponent}) and compares their position to the boss
 * slot's live position. Behavior by distance:
 * <ul>
 *   <li>&le; {@link #LEASH_RADIUS}: no-op.</li>
 *   <li>&gt; {@link #LEASH_RADIUS}, &le; {@link #TELEPORT_RADIUS}: call
 *       {@link NPCEntity#setLeashPoint} on the champion with the boss's current
 *       position. This is a soft nudge - it becomes an active pull only if the
 *       mob role's state machine defines a {@code ReturnToPost}-style
 *       {@link com.hypixel.hytale.server.npc.corecomponents.world.SensorLeash}
 *       with {@code BodyMotionSeek} (matches our Guard.json pattern). For roles
 *       without that state, the leash point is stored but not acted on: champs
 *       stay where they are until the teleport threshold triggers.</li>
 *   <li>&gt; {@link #TELEPORT_RADIUS}: hard {@link TransformComponent#teleportPosition}
 *       to a scattered spot near the boss. Catches strays that fell behind after
 *       combat or fast chunk-load bursts.</li>
 * </ul>
 *
 * <p>Idle suppression: before enforcing, we check the champion's {@code Role} via
 * {@link com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport#getEntityTargets}.
 * If any target slot (combat target, interaction target, etc.) holds a valid ref,
 * the champion is considered busy and we skip. Same canonical idle predicate we
 * use elsewhere for Nat20 NPCs.
 *
 * <p>If the boss slot is dead or its UUID doesn't resolve, the leash is released -
 * surviving champions roam freely.
 */
public class Nat20MobGroupLeashSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobGroupLeash");
    private static final Query<EntityStore> QUERY = Query.any();

    /** Champion stays within this range of the boss with no enforcement. */
    private static final double LEASH_RADIUS = 25.0;
    private static final double LEASH_RADIUS_SQ = LEASH_RADIUS * LEASH_RADIUS;

    /** Past this range, force-teleport the champion back to the boss. */
    private static final double TELEPORT_RADIUS = 50.0;
    private static final double TELEPORT_RADIUS_SQ = TELEPORT_RADIUS * TELEPORT_RADIUS;

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

        if (distSq <= LEASH_RADIUS_SQ) return;

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return;
        if (hasAnyTarget(npc)) return; // engaged (combat / interaction / etc.)
        npc.setLeashPoint(bossPos);

        if (distSq > TELEPORT_RADIUS_SQ) {
            Vector3d target = scatterAround(bossPos, TELEPORT_SCATTER);
            champT.teleportPosition(target);
            LOGGER.atFine().log(
                    "Leash teleport: group=%s slot=%d distSq=%.0f to (%.1f,%.1f,%.1f)",
                    member.getGroupKey(), member.getSlotIndex(), distSq,
                    target.getX(), target.getY(), target.getZ());
        }
    }

    private static SlotRecord findLiveBoss(MobGroupRecord record) {
        for (SlotRecord s : record.getSlots()) {
            if (s.isBoss() && !s.isDead() && s.getCurrentUuid() != null) return s;
        }
        return null;
    }

    /**
     * True if this NPC has any active target (combat, interaction, etc.) in any of
     * its MarkedEntitySupport slots. This mirrors the idle check Hytale's own role
     * state transitions use.
     */
    private static boolean hasAnyTarget(NPCEntity npc) {
        Role role = npc.getRole();
        if (role == null) return false;
        Ref<EntityStore>[] targets = role.getMarkedEntitySupport().getEntityTargets();
        if (targets == null) return false;
        for (Ref<EntityStore> t : targets) {
            if (t != null && t.isValid()) return true;
        }
        return false;
    }

    private Vector3d scatterAround(Vector3d center, double radius) {
        double dx = (rng.nextDouble() - 0.5) * 2 * radius;
        double dz = (rng.nextDouble() - 0.5) * 2 * radius;
        return new Vector3d(center.getX() + dx, center.getY(), center.getZ() + dz);
    }
}
