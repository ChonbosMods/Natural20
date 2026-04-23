package com.chonbosmods.dice;

import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.stats.Stat;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Nat20DiceRollerTest {

    private static PlayerStats statsWith(int strScore, int level) {
        int[] stats = new int[Stat.values().length];
        stats[Stat.STR.index()] = strScore;
        return new PlayerStats(stats, level, Set.of());
    }

    @Test
    void normalMode_rollsOneDie_otherRollIsSentinel() {
        SkillCheckRequest req = new SkillCheckRequest(Skill.ATHLETICS, null, 10, RollMode.NORMAL);
        SkillCheckResult r = Nat20DiceRoller.roll(statsWith(9, 1), req, new Random(42L));
        assertEquals(-1, r.otherRoll());
        assertEquals(RollMode.NORMAL, r.mode());
        assertTrue(r.naturalRoll() >= 1 && r.naturalRoll() <= 20);
    }

    @Test
    void advantageMode_keepsHigher() {
        SkillCheckRequest req = new SkillCheckRequest(Skill.ATHLETICS, null, 10, RollMode.ADVANTAGE);
        SkillCheckResult r = Nat20DiceRoller.roll(statsWith(9, 1), req, new Random(42L));
        assertEquals(RollMode.ADVANTAGE, r.mode());
        assertTrue(r.naturalRoll() >= r.otherRoll(), "kept die >= dropped die");
    }

    @Test
    void disadvantageMode_keepsLower() {
        SkillCheckRequest req = new SkillCheckRequest(Skill.ATHLETICS, null, 10, RollMode.DISADVANTAGE);
        SkillCheckResult r = Nat20DiceRoller.roll(statsWith(9, 1), req, new Random(42L));
        assertEquals(RollMode.DISADVANTAGE, r.mode());
        assertTrue(r.naturalRoll() <= r.otherRoll(), "kept die <= dropped die");
    }

    @Test
    void advantage_nat1AndNat17_keeps17_doesNotAutoFail() {
        Random forced = new Random() {
            private final int[] values = {1, 17};
            private int i = 0;
            @Override public int nextInt(int bound) { return values[i++] - 1; }
        };
        SkillCheckRequest req = new SkillCheckRequest(Skill.ATHLETICS, null, 10, RollMode.ADVANTAGE);
        SkillCheckResult r = Nat20DiceRoller.roll(statsWith(9, 1), req, forced);
        assertEquals(17, r.naturalRoll(), "kept die is 17");
        assertEquals(1, r.otherRoll(), "dropped die is 1");
        assertTrue(r.passed(), "17 + mods beats DC 10");
    }

    @Test
    void nat20OnKeptDie_autoPasses() {
        Random forced = new Random() {
            @Override public int nextInt(int bound) { return 19; }
        };
        SkillCheckRequest req = new SkillCheckRequest(Skill.ATHLETICS, null, 30, RollMode.NORMAL);
        SkillCheckResult r = Nat20DiceRoller.roll(statsWith(0, 1), req, forced);
        assertTrue(r.passed(), "nat 20 beats any DC");
    }

    @Test
    void nat1OnKeptDie_autoFails() {
        Random forced = new Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
        SkillCheckRequest req = new SkillCheckRequest(Skill.ATHLETICS, null, 5, RollMode.NORMAL);
        SkillCheckResult r = Nat20DiceRoller.roll(statsWith(30, 40), req, forced);
        assertFalse(r.passed(), "nat 1 fails any DC");
    }
}
