package com.chonbosmods.loot.mob.abilities;

import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Frostborn mob ability: slows attackers on hit and periodically slows nearby players.
 *
 * <p>Two triggers:
 * <ul>
 *   <li>{@link #onHurt}: apply slowness to the entity that attacked this mob.</li>
 *   <li>{@link #onTick}: periodically apply slowness to all players within a radius.</li>
 * </ul>
 *
 * <p>Actual slowness application requires entity-effect or stat-modifier SDK calls (TODO).
 */
public class FrostbornAbility implements MobAbilityHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /** Radius in blocks for the periodic slow aura. */
    private static final double AURA_RADIUS_BLOCKS = 6.0;

    /** Tick interval for the slow aura: fire every N ticks to avoid per-tick overhead. */
    private static final int AURA_TICK_INTERVAL = 20;

    /** Simple tick counter: not per-mob, but handlers are invoked per-mob each tick. */
    private int tickCounter;

    @Override
    public void onSpawn(Ref<EntityStore> mobRef, Store<EntityStore> store, Nat20MobAffixDef def) {
        LOGGER.atInfo().log("Frostborn ability initialized for mob %s (affix: %s)", mobRef, def.id());
    }

    @Override
    public void onTick(Ref<EntityStore> mobRef, Store<EntityStore> store) {
        tickCounter++;
        if (tickCounter % AURA_TICK_INTERVAL != 0) return;

        // TODO: Perform spatial query for players within AURA_RADIUS_BLOCKS of mob position.
        // For each player found, apply a short-duration slowness effect via
        // EffectControllerComponent or a MovementSpeed stat modifier.
        LOGGER.atFine().log("Frostborn aura tick for mob %s: slow players within %.1f blocks",
                mobRef, AURA_RADIUS_BLOCKS);
    }

    @Override
    public void onHurt(Ref<EntityStore> mobRef, Store<EntityStore> store, Damage event) {
        Damage.Source source = event.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        // TODO: Apply slowness EntityEffect to the attacker via entitySource.getRef().
        // Likely a MovementSpeed stat modifier with a timed removal, or a "Slowness"
        // EntityEffect on the attacker's EffectControllerComponent.
        LOGGER.atInfo().log("Frostborn proc: mob %s was hit for %.2f damage, slowing attacker %s",
                mobRef, event.getAmount(), entitySource.getRef());
    }
}
