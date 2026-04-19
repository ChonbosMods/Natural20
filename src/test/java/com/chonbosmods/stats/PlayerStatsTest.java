package com.chonbosmods.stats;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStatsTest {

    @Test
    void skillCheckModifier_matchesDnd5eFormula() {
        for (int score = 0; score <= 30; score++) {
            int expected = Math.floorDiv(score - 10, 2);
            assertEquals(expected, withScore(Stat.STR, score).getSkillCheckModifier(Stat.STR),
                    "score=" + score);
        }
    }

    @Test
    void skillCheckModifier_signedAtKeyScores() {
        assertEquals(-5, withScore(Stat.DEX, 1).getSkillCheckModifier(Stat.DEX));
        assertEquals(-4, withScore(Stat.DEX, 3).getSkillCheckModifier(Stat.DEX));
        assertEquals(-1, withScore(Stat.DEX, 8).getSkillCheckModifier(Stat.DEX));
        assertEquals(0, withScore(Stat.DEX, 10).getSkillCheckModifier(Stat.DEX));
        assertEquals(0, withScore(Stat.DEX, 11).getSkillCheckModifier(Stat.DEX));
        assertEquals(2, withScore(Stat.DEX, 14).getSkillCheckModifier(Stat.DEX));
        assertEquals(5, withScore(Stat.DEX, 20).getSkillCheckModifier(Stat.DEX));
        assertEquals(10, withScore(Stat.DEX, 30).getSkillCheckModifier(Stat.DEX));
    }

    @Test
    void powerModifier_neverNegative() {
        for (int score = 0; score <= 30; score++) {
            int mod = withScore(Stat.CHA, score).getPowerModifier(Stat.CHA);
            assertTrue(mod >= 0, "score=" + score + " mod=" + mod);
        }
    }

    @Test
    void powerModifier_matchesFloorDivByThree() {
        for (int score = 0; score <= 30; score++) {
            int expected = Math.floorDiv(score, 3);
            assertEquals(expected, withScore(Stat.INT, score).getPowerModifier(Stat.INT),
                    "score=" + score);
        }
    }

    @Test
    void powerModifier_scaledAtKeyScores() {
        assertEquals(0, withScore(Stat.STR, 0).getPowerModifier(Stat.STR));
        assertEquals(0, withScore(Stat.STR, 2).getPowerModifier(Stat.STR));
        assertEquals(1, withScore(Stat.STR, 3).getPowerModifier(Stat.STR));
        assertEquals(3, withScore(Stat.STR, 9).getPowerModifier(Stat.STR));
        assertEquals(3, withScore(Stat.STR, 10).getPowerModifier(Stat.STR));
        assertEquals(5, withScore(Stat.STR, 15).getPowerModifier(Stat.STR));
        assertEquals(6, withScore(Stat.STR, 20).getPowerModifier(Stat.STR));
        assertEquals(10, withScore(Stat.STR, 30).getPowerModifier(Stat.STR));
    }

    @Test
    void twoModifiersDivergeBelowTen() {
        PlayerStats p = withScore(Stat.WIS, 6);
        assertEquals(-2, p.getSkillCheckModifier(Stat.WIS));
        assertEquals(2, p.getPowerModifier(Stat.WIS));
    }

    private static PlayerStats withScore(Stat stat, int score) {
        int[] stats = new int[Stat.values().length];
        stats[stat.index()] = score;
        return new PlayerStats(stats, 1, Set.of());
    }
}
