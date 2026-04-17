package com.chonbosmods.combat;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
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
 * <p>DOT lifetime is linked to the EntityEffect on the target: when the
 * EntityEffect expires (visuals end), the DOT stops dealing damage.
 * No independent duration timer.
 */
public class Nat20DotTickSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final float TICK_INTERVAL_S = 2.0f;
    private static final float MIN_DAMAGE_PER_TICK = 0.5f;
    private static final float DAMAGE_PER_TICK = 0.5f;

    /**
     * Check if a damage event is a DOT tick by matching its cause against
     * our DOT damage causes. Weapon-scanning systems should call this and
     * skip if true, so they don't re-trigger based on the attacker's current weapon.
     */
    public static boolean isDotTickDamage(Damage damage) {
        int causeIdx = damage.getDamageCauseIndex();
        return causeIdx == dotCauseBleed || causeIdx == dotCauseFire
                || causeIdx == dotCauseIce || causeIdx == dotCausePoison
                || causeIdx == dotCauseVoid;
    }

    private static int dotCauseBleed = Integer.MIN_VALUE;
    private static int dotCauseFire = Integer.MIN_VALUE;
    private static int dotCauseIce = Integer.MIN_VALUE;
    private static int dotCausePoison = Integer.MIN_VALUE;
    private static int dotCauseVoid = Integer.MIN_VALUE;
    private static boolean dotCausesResolved;

    public enum DotType {
        BLEED, IGNITE, COLD, INFECT, CORRUPT
    }

    public static final float MIN_DURATION = 5.0f;
    public static final float MAX_DURATION = 15.0f;

    private static class DotEntry {
        final Ref<EntityStore> attackerRef;
        final float damagePerTick;
        final float initialDuration;
        float remainingDuration;

        DotEntry(Ref<EntityStore> attackerRef, float damagePerTick, float duration) {
            this.attackerRef = attackerRef;
            this.damagePerTick = damagePerTick;
            this.initialDuration = duration;
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

    // Hex visual refresh: re-apply EntityEffect every tick cycle while hex is active.
    // Particle LifeSpan is 2.5s, tick interval is 2s, so particles stay continuous.
    // When hex is consumed, removeHexVisual() is called and re-application stops.
    private final ConcurrentHashMap<Ref<EntityStore>, Ref<EntityStore>> hexVisualTargets = new ConcurrentHashMap<>();
    private EntityEffect hexEffect;
    private boolean hexEffectResolved;

    // DamageCause indices per DotType
    private final int[] causeIndices = new int[DotType.values().length];
    // EntityEffect objects per DotType (for hasEffect check)
    private final EntityEffect[] effectObjects = new EntityEffect[DotType.values().length];
    private boolean resolved;

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
                               Ref<EntityStore> attackerRef, float damagePerTick, float duration) {
        EntityDotState state = trackedEntities.get(targetRef);
        if (state == null) {
            state = new EntityDotState(TICK_INTERVAL_S);
            EntityDotState existing = trackedEntities.putIfAbsent(targetRef, state);
            if (existing != null) state = existing;
        }
        DotEntry newEntry = new DotEntry(attackerRef,
                Math.max(damagePerTick, MIN_DAMAGE_PER_TICK), duration);
        DotEntry previous = state.dots.putIfAbsent(type, newEntry);
        return previous == null;
    }

    /**
     * Remove all DOT state for an entity (e.g., on death).
     */
    public void removeEntity(Ref<EntityStore> targetRef) {
        trackedEntities.remove(targetRef);
        hexVisualTargets.remove(targetRef);
    }

    /** Start refreshing hex skull particle on target. Called by HexSystem on application. */
    public void addHexVisual(Ref<EntityStore> targetRef) {
        hexVisualTargets.put(targetRef, targetRef);
        // Ensure entity is tracked so tick() runs for it
        trackedEntities.computeIfAbsent(targetRef, k -> new EntityDotState(TICK_INTERVAL_S));
    }

    /** Stop refreshing hex skull particle. Called by HexConsumeSystem on consumption. */
    public void removeHexVisual(Ref<EntityStore> targetRef) {
        hexVisualTargets.remove(targetRef);
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

        if (!resolved) {
            resolveIndices();
        }

        // Advance timer
        state.timeUntilNextTick -= dt;

        // Primary: duration countdown. Secondary: hasEffect early-exit after 3s.
        EffectControllerComponent effectCtrl =
                store.getComponent(ref, EffectControllerComponent.getComponentType());
        state.dots.entrySet().removeIf(e -> {
            DotEntry dot = e.getValue();
            dot.remainingDuration -= dt;
            if (dot.remainingDuration <= 0) return true;

            // After 3s of running, also check if the EntityEffect expired early
            if (dot.remainingDuration < (dot.initialDuration - 3.0f) && effectCtrl != null) {
                EntityEffect ef = effectObjects[e.getKey().ordinal()];
                if (ef != null && !effectCtrl.hasEffect(ef)) return true;
            }
            return false;
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
                        new Damage(new Damage.EntitySource(dot.attackerRef), causeIdx, dot.damagePerTick));
            } catch (Exception e) {
                LOGGER.atWarning().log("[DotTick] damage dispatch failed for %s on %s: %s",
                        entry.getKey(), ref, e.getMessage());
            }
        }
    }

    private void refreshHexVisual(Ref<EntityStore> ref, Store<EntityStore> store,
                                   CommandBuffer<EntityStore> commandBuffer) {
        if (!hexEffectResolved) {
            hexEffect = EntityEffect.getAssetMap().getAsset("Nat20HexEffect");
            hexEffectResolved = true;
        }
        if (hexEffect == null) return;

        EffectControllerComponent effectCtrl =
                store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effectCtrl != null) {
            effectCtrl.addEffect(ref, hexEffect, commandBuffer);
        }
    }

    private void resolveIndices() {
        var causeMap = DamageCause.getAssetMap();
        causeIndices[DotType.BLEED.ordinal()] = causeMap.getIndex("Nat20Bleed");
        causeIndices[DotType.IGNITE.ordinal()] = causeMap.getIndex("Nat20Fire");
        causeIndices[DotType.COLD.ordinal()] = causeMap.getIndex("Nat20Ice");
        causeIndices[DotType.INFECT.ordinal()] = causeMap.getIndex("Nat20Poison");
        causeIndices[DotType.CORRUPT.ordinal()] = causeMap.getIndex("Nat20Void");

        var effectMap = EntityEffect.getAssetMap();
        effectObjects[DotType.BLEED.ordinal()] = effectMap.getAsset("Nat20BleedEffect");
        effectObjects[DotType.IGNITE.ordinal()] = effectMap.getAsset("Nat20IgniteEffect");
        effectObjects[DotType.COLD.ordinal()] = effectMap.getAsset("Nat20ColdEffect");
        effectObjects[DotType.INFECT.ordinal()] = effectMap.getAsset("Nat20InfectEffect");
        effectObjects[DotType.CORRUPT.ordinal()] = effectMap.getAsset("Nat20CorruptEffect");

        // Also populate static DOT cause indices for isDotTickDamage()
        if (!dotCausesResolved) {
            dotCauseBleed = causeIndices[DotType.BLEED.ordinal()];
            dotCauseFire = causeIndices[DotType.IGNITE.ordinal()];
            dotCauseIce = causeIndices[DotType.COLD.ordinal()];
            dotCausePoison = causeIndices[DotType.INFECT.ordinal()];
            dotCauseVoid = causeIndices[DotType.CORRUPT.ordinal()];
            dotCausesResolved = true;
        }

        resolved = true;
        LOGGER.atInfo().log("[DotTick] resolved: causes=[%d,%d,%d,%d,%d] effects=[%s,%s,%s,%s,%s]",
                causeIndices[0], causeIndices[1], causeIndices[2], causeIndices[3], causeIndices[4],
                effectObjects[0] != null, effectObjects[1] != null, effectObjects[2] != null,
                effectObjects[3] != null, effectObjects[4] != null);
    }
}
