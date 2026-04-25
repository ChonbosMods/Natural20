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

    private static final double EPS = 1e-9;

    @Test
    void ilvlScaleFloorAtIlvl1IsThirtyPercentOfEndgameForCommon() {
        // qv=1 (Common), ilvl=1 -> spread=0.30 x endgameScale=2.10 = 0.630
        assertEquals(0.630, Nat20XpMath.ilvlScale(1, 1), EPS);
    }

    @Test
    void ilvlScaleFloorAtIlvl1IsThirtyPercentOfEndgameForLegendary() {
        // qv=5 (Legendary), ilvl=1 -> spread=0.30 x endgameScale=2.628 = 0.7884
        assertEquals(0.7884, Nat20XpMath.ilvlScale(1, 5), EPS);
    }

    @Test
    void ilvlScaleAtIlvl45MatchesTodaysValueForCommon() {
        // qv=1, ilvl=45 -> spread=1.0 x endgameScale=2.10 = 2.10 (today's value preserved)
        assertEquals(2.100, Nat20XpMath.ilvlScale(45, 1), EPS);
    }

    @Test
    void ilvlScaleAtIlvl45MatchesTodaysValueForLegendary() {
        // qv=5, ilvl=45 -> spread=1.0 x endgameScale=2.628 (today's value preserved)
        assertEquals(2.628, Nat20XpMath.ilvlScale(45, 5), EPS);
    }

    @Test
    void ilvlScaleMidpointIsLinearBetweenFloorAndCeiling() {
        // qv=1, ilvl=23 -> spread = 0.30 + 0.70 x 22/44 = 0.65
        // scale = 0.65 x 2.10 = 1.365
        assertEquals(1.365, Nat20XpMath.ilvlScale(23, 1), EPS);
    }

    @Test
    void ilvlScaleHigherRarityScalesProportionallyHigher() {
        // At ilvl 1, Legendary should be exactly endgameScale_legendary / endgameScale_common
        // higher than Common: 0.7884 / 0.630 = 2.628 / 2.10 = 1.252
        double common = Nat20XpMath.ilvlScale(1, 1);
        double legendary = Nat20XpMath.ilvlScale(1, 5);
        assertEquals(2.628 / 2.100, legendary / common, EPS);
    }
}
