package com.chonbosmods.loot.effects;

import com.chonbosmods.loot.def.Nat20AffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;

/**
 * Vampiric life steal: heals the attacker for a fraction of damage dealt on hit.
 *
 * <p>The {@code effectiveValue} represents the life steal percentage (e.g., 0.05 = 5%).
 * Healing amount is {@code damage * effectiveValue}.
 */
public class VampiricEffectHandler implements EffectHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    @Override
    public void onHit(Nat20AffixDef def, double effectiveValue, Damage event) {
        if (effectiveValue <= 0) return;

        Damage.Source source = event.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        double damageDealt = event.getAmount();
        double healAmount = damageDealt * effectiveValue;

        if (healAmount <= 0) return;

        // TODO: Apply healing to the attacker. The exact SDK mechanism for direct healing
        // is uncertain: candidates include EntityStatMap health modifier, EffectControllerComponent
        // with a heal EntityEffect, or a direct health setter on the Player component.
        // For now, log the computed heal amount for runtime testing.
        LOGGER.atInfo().log("Vampiric proc: %.2f damage * %.1f%% = %.2f heal",
                damageDealt, effectiveValue * 100.0, healAmount);
    }
}
