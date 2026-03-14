package com.chonbosmods.loot.effects;

/**
 * Revitalizing health regeneration: a passive periodic effect.
 *
 * <p>This affix targets the "Regeneration" stat and is handled entirely through the modifier
 * manager's STAT path, which applies a regeneration stat modifier when the armor is equipped.
 * The EFFECT handler is intentionally a no-op stub: all regeneration behavior comes from
 * the stat modifier on equip, not from event-driven hooks.
 *
 * <p>This class exists so the handler registry has an entry for {@code nat20:revitalizing},
 * preventing "no handler found" warnings during affix processing.
 */
public class RevitalizingEffectHandler implements EffectHandler {
    // All methods use the default no-op implementations from EffectHandler.
    // Regeneration is applied via stat modifiers on equip, not through event hooks.
}
