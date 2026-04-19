package com.chonbosmods.progression;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Nat20XpMathTest {

    @Test
    void bonusRangeByTier() {
        Random r = new Random(42);
        Set<Integer> t1 = new HashSet<>();
        Set<Integer> t2 = new HashSet<>();
        Set<Integer> t3 = new HashSet<>();
        Set<Integer> t4 = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            t1.add(Nat20XpMath.rollBonusPerZone(1, r));
            t2.add(Nat20XpMath.rollBonusPerZone(2, r));
            t3.add(Nat20XpMath.rollBonusPerZone(3, r));
            t4.add(Nat20XpMath.rollBonusPerZone(4, r));
        }
        assertEquals(Set.of(6, 7, 8), t1);
        assertEquals(Set.of(4, 5, 6), t2);
        assertEquals(Set.of(3, 4), t3);
        assertEquals(Set.of(1, 2, 3), t4);
    }

    @Test
    void tierClamps() {
        Random r = new Random(1);
        int low = Nat20XpMath.rollBonusPerZone(0, r);
        int high = Nat20XpMath.rollBonusPerZone(9, r);
        assertTrue(low >= 6 && low <= 8, "tier 0 should clamp to T1 range [6,8]");
        assertTrue(high >= 1 && high <= 3, "tier 9 should clamp to T4 range [1,3]");
    }
}
