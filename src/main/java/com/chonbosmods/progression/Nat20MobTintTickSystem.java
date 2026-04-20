package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodic re-apply of difficulty tint {@link EntityEffect} on every tiered mob.
 *
 * <p>Native Hytale chunk persistence restores the entity and its components (including
 * {@link Nat20MobLevel} with its stored difficulty tier) but does not reliably re-render
 * the persistent tint overlay on the client. Trying to force a resync from the reconcile
 * listener failed in every variant (Remove+Add same-tick, deferred-Add, despawn+respawn):
 * client-side overlay rebuild depends on ApplicationEffects firing from a fresh
 * {@code EffectOp.Add}, and intermediate server-side tricks don't reliably produce one.
 *
 * <p>This system takes the opposite approach: treat the tint like
 * {@link com.chonbosmods.combat.Nat20DotTickSystem}'s hex-visual refresh - periodically
 * fire {@code addEffect} on every qualifying entity. Each call produces a fresh Add op,
 * which the client uses to (re-)apply the shader overlay. The tint effect's
 * {@code ApplicationEffects} is only the top/bottom shader tints (no particles, no sound),
 * so repeated Add ops cause no visible stacking or audio artifacts.
 *
 * <p>Cadence is held at {@link #TICK_INTERVAL_MS} per entity so we don't spam the network
 * every server tick. Any mob whose visual drifts (fresh spawn, chunk reload, server
 * restart) is self-healed within one tick interval.
 */
public class Nat20MobTintTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobTintTick");
    private static final Query<EntityStore> QUERY = Query.any();

    /** How often each entity gets its tint re-applied. The per-tick early-return cost
     *  (component lookup + map check) is paid regardless of this interval, so lowering
     *  only affects network Add-op frequency for tiered mobs. Precedent: the DOT tick
     *  system refreshes the hex visual at 2s without issue; 3s here gives responsive
     *  self-healing on chunk reload / server restart with minimal network load. */
    private static final long TICK_INTERVAL_MS = 3_000L;

    /** Per-entity "next apply due at" timestamp. Entities without Nat20MobLevel never
     *  get an entry; entries for dead refs are cleaned up lazily via ref.isValid(). */
    private final ConcurrentHashMap<Ref<EntityStore>, Long> nextApplyAt = new ConcurrentHashMap<>();

    /** Cache of EntityEffect asset per DifficultyTier, resolved lazily on first tick. */
    private final EntityEffect[] tintEffects = new EntityEffect[DifficultyTier.values().length];
    private boolean effectsResolved = false;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() { return QUERY; }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (!ref.isValid()) {
            nextApplyAt.remove(ref);
            return;
        }

        Nat20MobLevel level = store.getComponent(ref, Natural20.getMobLevelType());
        if (level == null) return;

        DifficultyTier tier = level.getDifficultyTier();
        if (tier == null) return;

        long now = System.currentTimeMillis();
        Long next = nextApplyAt.get(ref);
        if (next != null && now < next) return;

        if (!effectsResolved) resolveEffects();

        EntityEffect effect = tintEffects[tier.ordinal()];
        if (effect == null) return;

        EffectControllerComponent ctrl =
                store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return;

        // Direct addEffect (not applyOnce) - fires EffectOp.Add every cycle so the client
        // rebuilds the shader overlay even if chunk-reload/server-restart dropped it.
        // Safe because the tint EntityEffect has only top/bottom-tint ApplicationEffects
        // (no particles or sound), so repeat Adds produce no audible/visible stacking.
        ctrl.addEffect(ref, effect, commandBuffer);

        nextApplyAt.put(ref, now + TICK_INTERVAL_MS);
    }

    private void resolveEffects() {
        var assetMap = EntityEffect.getAssetMap();
        for (DifficultyTier t : DifficultyTier.values()) {
            String id = t.tintEffectId();
            if (id == null) continue;
            EntityEffect asset = assetMap.getAsset(id);
            if (asset == null) {
                LOGGER.atWarning().log("Missing EntityEffect asset '%s' for tier %s", id, t);
            }
            tintEffects[t.ordinal()] = asset;
        }
        effectsResolved = true;
    }
}
