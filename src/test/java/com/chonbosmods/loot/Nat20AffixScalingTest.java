package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Nat20AffixScalingTest {

    private static final double EPS = 1e-9;
    private static Nat20RarityRegistry RARITY;

    @BeforeAll
    static void loadRarities() {
        RARITY = new Nat20RarityRegistry();
        RARITY.loadAll(null);  // loads Common (qv=1) ... Legendary (qv=5) from classpath
    }

    @Test
    void interpolateScalableTrueAppliesIlvlScale() {
        // range [1.0, 1.0] (min == max so lootLevel doesn't matter), Common (qv=1), ilvl=1
        // Expected: 1.0 × ilvlScale(1, 1) = 1.0 × 0.630 = 0.630
        AffixValueRange range = new AffixValueRange(1.0, 1.0);
        Nat20LootData data = new Nat20LootData();
        data.setItemLevel(1);
        data.setRarity("Common");

        double v = Nat20AffixScaling.interpolate(range, 0.5, data, RARITY, true);
        assertEquals(0.630, v, EPS);
    }

    @Test
    void interpolateScalableFalseSkipsIlvlScale() {
        // Same inputs as above, ilvlScalable=false
        // Expected: 1.0 (unscaled, range.interpolate(0.5) on [1.0, 1.0] = 1.0)
        AffixValueRange range = new AffixValueRange(1.0, 1.0);
        Nat20LootData data = new Nat20LootData();
        data.setItemLevel(1);
        data.setRarity("Common");

        double v = Nat20AffixScaling.interpolate(range, 0.5, data, RARITY, false);
        assertEquals(1.0, v, EPS);
    }

    @Test
    void interpolateScalableFalseStaysIntegerStableAcrossIlvls() {
        // Legendary stat-score: range [2.0, 2.0]. ilvlScalable=false should yield 2.0
        // at every ilvl, regardless of rarity-driven qv lookup (which the false path bypasses).
        AffixValueRange range = new AffixValueRange(2.0, 2.0);
        Nat20LootData ilvl1 = new Nat20LootData(); ilvl1.setItemLevel(1); ilvl1.setRarity("Legendary");
        Nat20LootData ilvl45 = new Nat20LootData(); ilvl45.setItemLevel(45); ilvl45.setRarity("Legendary");

        double v1 = Nat20AffixScaling.interpolate(range, 0.5, ilvl1, RARITY, false);
        double v45 = Nat20AffixScaling.interpolate(range, 0.5, ilvl45, RARITY, false);

        assertEquals(2.0, v1, EPS);
        assertEquals(2.0, v45, EPS);
    }
}
