package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;

/**
 * Helpers for resolving the ilvl + quality scaling factor at affix read time.
 * Centralises the {@code rarity registry -> qualityValue} lookup so call sites
 * can do a single line instead of three.
 */
public final class Nat20AffixScaling {

    private Nat20AffixScaling() {}

    /** Resolve qualityValue (1..5) for an item, defaulting to 1 if the rarity isn't found. */
    public static int qualityValueOf(Nat20LootData lootData, Nat20RarityRegistry rarityRegistry) {
        Nat20RarityDef def = rarityRegistry.get(lootData.getRarity());
        return def != null ? def.qualityValue() : 1;
    }

    /**
     * Interpolate an affix value at the given loot level, optionally applying ilvl + quality scaling.
     *
     * <p>When {@code ilvlScalable} is true, scales by the item's ilvl and the rarity-derived
     * quality value (qv 1..5). When false, returns the unscaled lerp
     * {@code range.interpolate(lootLevel)}; the {@code lootData} and
     * {@code rarityRegistry} parameters are ignored. Stat-score affixes use the false path
     * because integer stability matters more than scaling.
     */
    public static double interpolate(AffixValueRange range, double lootLevel,
                                      Nat20LootData lootData, Nat20RarityRegistry rarityRegistry,
                                      boolean ilvlScalable) {
        if (!ilvlScalable) return range.interpolate(lootLevel);
        int qv = qualityValueOf(lootData, rarityRegistry);
        return range.interpolate(lootLevel, lootData.getItemLevel(), qv);
    }
}
