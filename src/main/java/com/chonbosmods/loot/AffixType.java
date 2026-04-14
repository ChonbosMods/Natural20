package com.chonbosmods.loot;

public enum AffixType {
    STAT,
    EFFECT,
    ABILITY,
    /**
     * Meta-type used only in {@code LootRules} entries, never on an individual affix definition.
     * Instructs the loot pipeline to pull from the union of all type pools, so a single rule slot
     * can roll STAT, EFFECT, or ABILITY interchangeably for better variety.
     */
    ANY
}
