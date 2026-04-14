package com.chonbosmods.loot.effects;

import com.chonbosmods.loot.def.Nat20AffixDef;
import com.google.common.flogger.FluentLogger;

/**
 * Fortified: percent chance (from {@code effectiveValue}) to negate a durability loss event.
 *
 * <p>Passive effect applied at durability-consumption time. Implementation TODO requires
 * locating Hytale's tool durability-decrement path and inserting a roll gate that skips
 * the decrement when this affix is present on the tool.
 */
public class FortifiedHandler implements EffectHandler {
    // No event callbacks: this affix is applied at durability-consumption time.
}
