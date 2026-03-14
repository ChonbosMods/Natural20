package com.chonbosmods.loot.mob.abilities;

import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Fiery mob ability: when the mob is hit, ignite the attacker (fire trail on move).
 *
 * <p>Triggered on {@link #onHurt}: the mob retaliates by setting the attacker ablaze.
 * The actual fire EntityEffect application is a TODO pending SDK entity-effect API discovery.
 */
public class FieryAbility implements MobAbilityHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    @Override
    public void onSpawn(Ref<EntityStore> mobRef, Store<EntityStore> store, Nat20MobAffixDef def) {
        LOGGER.atInfo().log("Fiery ability initialized for mob %s (affix: %s)", mobRef, def.id());
    }

    @Override
    public void onHurt(Ref<EntityStore> mobRef, Store<EntityStore> store, Damage event) {
        Damage.Source source = event.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        // TODO: Apply fire EntityEffect to the attacker entity via entitySource.getRef().
        // Candidates: EffectControllerComponent.addEffect() with a "Burning" or "OnFire"
        // EntityEffect, or a direct status-effect setter on the attacker's entity store.
        LOGGER.atInfo().log("Fiery proc: mob %s was hit for %.2f damage, igniting attacker %s",
                mobRef, event.getAmount(), entitySource.getRef());
    }
}
