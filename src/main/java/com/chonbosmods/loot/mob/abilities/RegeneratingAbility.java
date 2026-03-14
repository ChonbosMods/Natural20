package com.chonbosmods.loot.mob.abilities;

import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Regenerating mob ability: passively heals 2% max HP per second when out of combat.
 *
 * <p>Tracks the last time each mob was hurt. If enough time has elapsed since the last
 * combat event, the mob regenerates health on tick. The actual healing call is a TODO
 * pending SDK health-modification API discovery.
 */
public class RegeneratingAbility implements MobAbilityHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /** Time in milliseconds a mob must be out of combat before regen starts. */
    private static final long OUT_OF_COMBAT_THRESHOLD_MS = 5_000L;

    /** Regen rate: fraction of max HP per second. */
    private static final double REGEN_RATE_PER_SECOND = 0.02;

    /** Regen tick interval: apply regen every N ticks (20 ticks = 1 second). */
    private static final int REGEN_TICK_INTERVAL = 20;

    /** Per-mob last-hurt timestamp (millis). */
    private final ConcurrentHashMap<Ref<EntityStore>, Long> lastHurtTime = new ConcurrentHashMap<>();

    /** Per-mob tick counter for regen interval tracking. */
    private final ConcurrentHashMap<Ref<EntityStore>, Integer> tickCounters = new ConcurrentHashMap<>();

    @Override
    public void onSpawn(Ref<EntityStore> mobRef, Store<EntityStore> store, Nat20MobAffixDef def) {
        LOGGER.atInfo().log("Regenerating ability initialized for mob %s (affix: %s)", mobRef, def.id());
    }

    @Override
    public void onHurt(Ref<EntityStore> mobRef, Store<EntityStore> store, Damage event) {
        lastHurtTime.put(mobRef, System.currentTimeMillis());
    }

    @Override
    public void onTick(Ref<EntityStore> mobRef, Store<EntityStore> store) {
        int tick = tickCounters.merge(mobRef, 1, Integer::sum);
        if (tick % REGEN_TICK_INTERVAL != 0) return;

        long now = System.currentTimeMillis();
        Long lastHurt = lastHurtTime.get(mobRef);

        // If mob has never been hurt, or enough time has passed since last combat, regen
        if (lastHurt != null && (now - lastHurt) < OUT_OF_COMBAT_THRESHOLD_MS) {
            return; // Still in combat
        }

        // TODO: Read mob's current and max health from EntityStatMap.
        // Heal by (maxHealth * REGEN_RATE_PER_SECOND). Clamp to max health.
        // Requires health stat read + a way to set current health (direct setter or heal effect).
        LOGGER.atFine().log("Regenerating tick: mob %s heals %.0f%% max HP (out of combat for %dms)",
                mobRef, REGEN_RATE_PER_SECOND * 100.0,
                lastHurt != null ? (now - lastHurt) : -1L);
    }

    @Override
    public void clearMob(Ref<EntityStore> mobRef) {
        lastHurtTime.remove(mobRef);
        tickCounters.remove(mobRef);
    }
}
