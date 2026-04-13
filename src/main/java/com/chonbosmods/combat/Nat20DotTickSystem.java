package com.chonbosmods.combat;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified DOT tick system: ensures all DOTs on the same entity tick at one
 * synchronized interval. When a new DOT is applied to an entity that already
 * has active DOTs, the new DOT syncs to the existing tick phase instead of
 * starting its own independent timer.
 *
 * <p>EntityEffect JSONs handle visuals only (tint, particles). This system
 * handles all DOT damage dispatch through the pipeline.
 */
public class Nat20DotTickSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final float TICK_INTERVAL_S = 2.0f;
    private static final float DAMAGE_PER_TICK = 0.5f;

    public enum DotType {
        BLEED, IGNITE, COLD, INFECT, CORRUPT
    }

    private static class DotEntry {
        final Ref<EntityStore> attackerRef;
        float remainingDuration;

        DotEntry(Ref<EntityStore> attackerRef, float duration) {
            this.attackerRef = attackerRef;
            this.remainingDuration = duration;
        }
    }

    private static class EntityDotState {
        float timeUntilNextTick;
        final Map<DotType, DotEntry> dots = new EnumMap<>(DotType.class);

        EntityDotState(float initialDelay) {
            this.timeUntilNextTick = initialDelay;
        }
    }

    private final ConcurrentHashMap<Ref<EntityStore>, EntityDotState> trackedEntities = new ConcurrentHashMap<>();

    // DamageCause indices per DotType
    private final int[] causeIndices = new int[DotType.values().length];
    private boolean causesResolved;

    /**
     * Register a DOT on a target entity. If the target already has active DOTs,
     * the new DOT syncs to the existing tick phase. If this is the first DOT,
     * a new tick phase starts.
     *
     * @return true if this is a new DOT type on this entity (caller should apply
     *         the visual EntityEffect), false if refreshing an existing DOT
     *         (caller should skip addEffect to prevent particle stacking)
     */
    public boolean registerDot(Ref<EntityStore> targetRef, DotType type,
                               Ref<EntityStore> attackerRef, float durationSeconds) {
        EntityDotState state = trackedEntities.get(targetRef);
        if (state == null) {
            state = new EntityDotState(TICK_INTERVAL_S);
            EntityDotState existing = trackedEntities.putIfAbsent(targetRef, state);
            if (existing != null) state = existing;
        }
        DotEntry previous = state.dots.put(type, new DotEntry(attackerRef, durationSeconds));
        return previous == null;
    }

    /**
     * Remove all DOT state for an entity (e.g., on death).
     */
    public void removeEntity(Ref<EntityStore> targetRef) {
        trackedEntities.remove(targetRef);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        EntityDotState state = trackedEntities.get(ref);
        if (state == null) return;

        if (!ref.isValid()) {
            trackedEntities.remove(ref);
            return;
        }

        if (!causesResolved) {
            resolveCauses();
        }

        // Advance timer
        state.timeUntilNextTick -= dt;

        // Tick expired DOTs out
        state.dots.entrySet().removeIf(e -> {
            e.getValue().remainingDuration -= dt;
            return e.getValue().remainingDuration <= 0;
        });

        if (state.dots.isEmpty()) {
            trackedEntities.remove(ref);
            return;
        }

        // Check if it's time to tick
        if (state.timeUntilNextTick > 0) return;

        // Reset timer for next tick
        state.timeUntilNextTick += TICK_INTERVAL_S;

        // Deal damage for all active DOTs at once
        for (var entry : state.dots.entrySet()) {
            int causeIdx = causeIndices[entry.getKey().ordinal()];
            if (causeIdx < 0) continue;

            DotEntry dot = entry.getValue();
            if (dot.attackerRef == null || !dot.attackerRef.isValid()) continue;

            try {
                commandBuffer.invoke(ref,
                        new Damage(new Damage.EntitySource(dot.attackerRef), causeIdx, DAMAGE_PER_TICK));
            } catch (Exception e) {
                LOGGER.atWarning().log("[DotTick] damage dispatch failed for %s on %s: %s",
                        entry.getKey(), ref, e.getMessage());
            }
        }
    }

    private void resolveCauses() {
        var assetMap = DamageCause.getAssetMap();
        causeIndices[DotType.BLEED.ordinal()] = assetMap.getIndex("Nat20Bleed");
        causeIndices[DotType.IGNITE.ordinal()] = assetMap.getIndex("Nat20Fire");
        causeIndices[DotType.COLD.ordinal()] = assetMap.getIndex("Nat20Ice");
        causeIndices[DotType.INFECT.ordinal()] = assetMap.getIndex("Nat20Poison");
        causeIndices[DotType.CORRUPT.ordinal()] = assetMap.getIndex("Nat20Void");
        causesResolved = true;
        LOGGER.atInfo().log("[DotTick] resolved causes: bleed=%d fire=%d ice=%d poison=%d void=%d",
                causeIndices[0], causeIndices[1], causeIndices[2], causeIndices[3], causeIndices[4]);
    }
}
