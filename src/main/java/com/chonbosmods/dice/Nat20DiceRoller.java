package com.chonbosmods.dice;

import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class Nat20DiceRoller {

    private Nat20DiceRoller() {
    }

    public static SkillCheckResult roll(PlayerStats stats, SkillCheckRequest request) {
        return roll(stats, request, ThreadLocalRandom.current());
    }

    public static SkillCheckResult roll(PlayerStats stats, SkillCheckRequest request, Random rng) {
        int first = rng.nextInt(20) + 1;
        int kept;
        int other;

        switch (request.mode()) {
            case ADVANTAGE -> {
                int second = rng.nextInt(20) + 1;
                kept = Math.max(first, second);
                other = Math.min(first, second);
            }
            case DISADVANTAGE -> {
                int second = rng.nextInt(20) + 1;
                kept = Math.min(first, second);
                other = Math.max(first, second);
            }
            default -> {
                kept = first;
                other = -1;
            }
        }

        Stat effectiveStat = request.stat() != null ? request.stat() : request.skill().getAssociatedStat();
        int statModifier = stats.getSkillCheckModifier(effectiveStat);
        int proficiencyBonus = stats.isProficient(request.skill()) ? stats.getProficiencyBonus() : 0;
        int totalRoll = kept + statModifier + proficiencyBonus;

        boolean critical = kept == 20 || kept == 1;
        boolean passed = kept == 20 || (kept != 1 && totalRoll >= request.dc());

        return new SkillCheckResult(
                kept, other, request.mode(),
                statModifier, proficiencyBonus, totalRoll, request.dc(),
                passed, critical);
    }
}
