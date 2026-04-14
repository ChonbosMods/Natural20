package com.chonbosmods.loot.effects;

import com.chonbosmods.loot.def.Nat20AffixDef;
import com.google.common.flogger.FluentLogger;

/**
 * Haste: increases mining speed by the {@code effectiveValue} percentage.
 *
 * <p>This is a passive modifier: it has no event callback. Application happens at the point
 * the mining speed is computed (tool use / block-break hit-rate). Implementation TODO requires
 * locating Hytale's mining-speed calculation and injecting a per-player bonus sourced from
 * equipped-tool affixes.
 */
public class HasteHandler implements EffectHandler {
    // No event callbacks: this affix is applied at mining-speed calculation time, not at event time.
    // The handler exists so the registry has an entry and processAffixes won't warn.
}
