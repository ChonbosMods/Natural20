package com.chonbosmods.progression;

import com.chonbosmods.quest.model.DifficultyConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Nat20XpMathTest {

    private static DifficultyConfig difficulty(int xpAmount) {
        return new DifficultyConfig("test-" + xpAmount, xpAmount,
                "Common", "Common", 1, 1, false, 0, 1.0, 1.0);
    }

    @Test
    void questPhaseXpMediumMatchesLevelOnlyOverload() {
        // Medium (xpAmount=100) is the 1.0x baseline: the difficulty-scaled
        // overload must return the same number as the level-only overload.
        for (int level : new int[] {1, 5, 10, 11, 20, 30, 40}) {
            assertEquals(Nat20XpMath.questPhaseXp(level),
                    Nat20XpMath.questPhaseXp(level, difficulty(100)),
                    "level " + level + " medium should match level-only");
        }
    }

    @Test
    void questPhaseXpEasyIsHalfOfMedium() {
        int medium = Nat20XpMath.questPhaseXp(5, difficulty(100));
        int easy = Nat20XpMath.questPhaseXp(5, difficulty(50));
        // Easy = 0.5x, floor'd. Must be <= medium and within [medium/2 - 1, medium/2].
        assertTrue(easy <= medium / 2, "easy should be at most half of medium");
        assertTrue(easy >= medium / 2 - 1, "easy should round-down near half of medium");
    }

    @Test
    void questPhaseXpHardIsDoubleOfMedium() {
        int medium = Nat20XpMath.questPhaseXp(5, difficulty(100));
        int hard = Nat20XpMath.questPhaseXp(5, difficulty(200));
        assertEquals(medium * 2, hard, "hard should be exactly 2x medium (integer math)");
    }

    @Test
    void questPhaseXpClampsMinimumToOne() {
        // Degenerate xpAmount=0 difficulty: multiplier=0, floor=0 -> clamp to 1
        // so a malformed config still pays out.
        int xp = Nat20XpMath.questPhaseXp(1, difficulty(0));
        assertEquals(1, xp, "xpAmount=0 should clamp to 1, not 0");
    }

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
