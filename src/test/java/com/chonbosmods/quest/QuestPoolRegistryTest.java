package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QuestPoolRegistryTest {

    private QuestPoolRegistry loadFixture() {
        QuestPoolRegistry reg = new QuestPoolRegistry();
        Path dir = Paths.get("src/test/resources/fixtures");
        reg.loadTestCollectPool(dir.resolve("quest-pool-tier.json"));
        return reg;
    }

    @Test
    void tierFieldParsed() {
        QuestPoolRegistry reg = loadFixture();
        assertEquals(1, reg.findCollectById("Tier1_Item").tier());
        assertEquals(2, reg.findCollectById("Tier2_Item").tier());
        assertEquals(3, reg.findCollectById("Tier3_Item").tier());
        assertEquals(4, reg.findCollectById("Tier4_Item").tier());
    }

    @Test
    void missingTierDefaultsToOne() {
        QuestPoolRegistry reg = loadFixture();
        assertEquals(1, reg.findCollectById("NoTier_Item").tier(),
            "entries without tier must default to 1 (starter-safe)");
    }

    @Test
    void tierOutOfRangeDefaultsToOne() {
        QuestPoolRegistry reg = loadFixture();
        assertEquals(1, reg.findCollectById("TierZero_Item").tier(),
            "tier=0 is outside [1,4] and must default to 1");
        assertEquals(1, reg.findCollectById("TierFive_Item").tier(),
            "tier=5 is outside [1,4] and must default to 1");
    }

    @Test
    void tierNonNumericDefaultsToOne() {
        QuestPoolRegistry reg = loadFixture();
        assertEquals(1, reg.findCollectById("TierString_Item").tier(),
            "non-numeric tier (string) must default to 1");
    }

    @Test
    void bandFilterZone1() {
        QuestPoolRegistry reg = loadFixture();
        Random r = new Random(1);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(reg.randomCollectResource(1, r).tier());
        }
        assertEquals(Set.of(1, 2), seen, "zone 1 band is [1,2]");
    }

    @Test
    void bandFilterZone2() {
        QuestPoolRegistry reg = loadFixture();
        Random r = new Random(2);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(reg.randomCollectResource(2, r).tier());
        }
        assertEquals(Set.of(1, 2, 3), seen, "zone 2 band is [1,3]");
    }

    @Test
    void bandFilterZone4() {
        QuestPoolRegistry reg = loadFixture();
        Random r = new Random(3);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(reg.randomCollectResource(4, r).tier());
        }
        assertEquals(Set.of(3, 4), seen, "zone 4 band is [3,4]");
    }

    @Test
    void nativeTierWeightedDouble() {
        QuestPoolRegistry reg = loadFixture();
        Random r = new Random(100);
        int nativeCount = 0, edgeCount = 0;
        // Zone 2: native is tier 2, edges are tier 1 and tier 3.
        for (int i = 0; i < 10_000; i++) {
            int t = reg.randomCollectResource(2, r).tier();
            if (t == 2) nativeCount++;
            else edgeCount++;
        }
        // Fixture tier distribution after out-of-range/malformed entries default to 1:
        //   tier 1: Tier1_Item, NoTier_Item, TierZero_Item, TierFive_Item, TierString_Item (5 entries)
        //   tier 2: Tier2_Item (1 entry)
        //   tier 3: Tier3_Item (1 entry)
        //   tier 4: Tier4_Item (1 entry)
        // Zone 2 band is [1,3]. Weights: tier-1 x 5 x 1 = 5; tier-2 x 1 x 2 = 2; tier-3 x 1 x 1 = 1.
        // Total weight = 8. Native (tier 2) fraction = 2/8 = 0.25 (25%).
        double nativeFraction = nativeCount / 10_000.0;
        assertTrue(nativeFraction > 0.22 && nativeFraction < 0.28,
            "native tier should take ~25% of draws (2/8 weight), got " + nativeFraction);
    }
}
