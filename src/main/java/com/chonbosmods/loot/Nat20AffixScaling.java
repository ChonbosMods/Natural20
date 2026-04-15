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

    /** Convenience: interpolate with ilvl scaling, looking up qv from the rarity registry. */
    public static double interpolate(AffixValueRange range, double lootLevel,
                                      Nat20LootData lootData, Nat20RarityRegistry rarityRegistry) {
        int qv = qualityValueOf(lootData, rarityRegistry);
        return range.interpolate(lootLevel, lootData.getItemLevel(), qv);
    }
}
