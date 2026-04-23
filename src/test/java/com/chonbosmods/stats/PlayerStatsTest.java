package com.chonbosmods.stats;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStatsTest {

    @Test
    void skillCheckModifier_matchesFloorDivByThree() {
        for (int score = 0; score <= 30; score++) {
            int expected = Math.floorDiv(score, 3);
            assertEquals(expected, withScore(Stat.STR, score).getSkillCheckModifier(Stat.STR),
                    "score=" + score);
        }
    }

    @Test
    void skillCheckModifier_matchesPowerModifier() {
        for (int score = 0; score <= 30; score++) {
            PlayerStats p = withScore(Stat.DEX, score);
            assertEquals(p.getPowerModifier(Stat.DEX), p.getSkillCheckModifier(Stat.DEX),
                    "score=" + score);
        }
    }

    @Test
    void skillCheckModifier_neverNegative() {
        for (int score = 0; score <= 30; score++) {
            int mod = withScore(Stat.CHA, score).getSkillCheckModifier(Stat.CHA);
            assertTrue(mod >= 0, "score=" + score + " mod=" + mod);
        }
    }

    @Test
    void skillCheckModifier_atKeyScores() {
        assertEquals(0, withScore(Stat.DEX, 0).getSkillCheckModifier(Stat.DEX));
        assertEquals(1, withScore(Stat.DEX, 3).getSkillCheckModifier(Stat.DEX));
        assertEquals(3, withScore(Stat.DEX, 9).getSkillCheckModifier(Stat.DEX));
        assertEquals(5, withScore(Stat.DEX, 15).getSkillCheckModifier(Stat.DEX));
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
    void twoModifiersAgreeAtLowScores() {
        PlayerStats p = withScore(Stat.WIS, 6);
        assertEquals(2, p.getSkillCheckModifier(Stat.WIS));
        assertEquals(2, p.getPowerModifier(Stat.WIS));
    }

    @Test
    void proficiencyBonus_atTierBoundaries() {
        assertEquals(2, withLevel(1).getProficiencyBonus());
        assertEquals(2, withLevel(8).getProficiencyBonus());
        assertEquals(3, withLevel(9).getProficiencyBonus());
        assertEquals(3, withLevel(16).getProficiencyBonus());
        assertEquals(4, withLevel(17).getProficiencyBonus());
        assertEquals(4, withLevel(24).getProficiencyBonus());
        assertEquals(5, withLevel(25).getProficiencyBonus());
        assertEquals(5, withLevel(32).getProficiencyBonus());
        assertEquals(6, withLevel(33).getProficiencyBonus());
        assertEquals(6, withLevel(40).getProficiencyBonus());
    }

    private static PlayerStats withScore(Stat stat, int score) {
        int[] stats = new int[Stat.values().length];
        stats[stat.index()] = score;
        return new PlayerStats(stats, 1, Set.of());
    }

    private static PlayerStats withLevel(int level) {
        int[] stats = new int[Stat.values().length];
        return new PlayerStats(stats, level, Set.of());
    }
}
