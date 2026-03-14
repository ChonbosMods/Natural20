package com.chonbosmods.loot.mob.abilities;

import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Berserker mob ability: when the mob drops below 30% HP, buffs attack damage and speed.
 *
 * <p>Triggered on {@link #onHurt}: after taking damage, checks if the mob's health is
 * below the threshold. If so, applies a one-time buff. The actual health check and stat
 * modification are TODOs pending SDK API discovery.
 */
public class BerserkerAbility implements MobAbilityHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /** Health fraction threshold below which the berserker buff activates. */
    private static final double HP_THRESHOLD = 0.30;

    /** Per-mob tracking of whether the berserker buff has already been applied. */
    private final ConcurrentHashMap<Ref<EntityStore>, Boolean> buffApplied = new ConcurrentHashMap<>();

    @Override
    public void onSpawn(Ref<EntityStore> mobRef, Store<EntityStore> store, Nat20MobAffixDef def) {
        LOGGER.atInfo().log("Berserker ability initialized for mob %s (affix: %s)", mobRef, def.id());
    }

    @Override
    public void onHurt(Ref<EntityStore> mobRef, Store<EntityStore> store, Damage event) {
        // Skip if buff already applied: berserker is a one-time activation
        if (Boolean.TRUE.equals(buffApplied.get(mobRef))) return;

        // TODO: Read mob's current health and max health from EntityStatMap.
        // Check if (currentHealth / maxHealth) <= HP_THRESHOLD.
        // If below threshold, apply multiplicative stat modifiers:
        //   - AttackDamage: +50% (multiplier 1.5)
        //   - AttackSpeed: +30% (multiplier 1.3)
        // Use StaticModifier with MULTIPLICATIVE on EntityStatMap, keyed by
        // "nat20:mobability:berserker:AttackDamage" etc.
        //
        // For now, assume the check passes and log the activation.
        // In a real implementation, the health check would gate this block.

        LOGGER.atInfo().log("Berserker proc: mob %s took %.2f damage, checking if below %.0f%% HP threshold",
                mobRef, event.getAmount(), HP_THRESHOLD * 100.0);

        // Mark as pending: the actual buff application will happen once health check is implemented.
        // When health check is added, move buffApplied.put() inside the threshold-met branch.
    }

    /**
     * Clean up per-mob state when a mob is removed.
     * Should be called from {@link com.chonbosmods.loot.mob.Nat20MobAffixManager#clearMob}.
     */
    public void clearMob(Ref<EntityStore> mobRef) {
        buffApplied.remove(mobRef);
    }
}
