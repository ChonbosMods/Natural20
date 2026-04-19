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
        int naturalRoll = rng.nextInt(20) + 1;

        Stat effectiveStat = request.stat() != null ? request.stat() : request.skill().getAssociatedStat();
        int statModifier = stats.getSkillCheckModifier(effectiveStat);
        int proficiencyBonus = stats.isProficient(request.skill()) ? stats.getProficiencyBonus() : 0;

        int totalRoll = naturalRoll + statModifier + proficiencyBonus;

        boolean critical = naturalRoll == 20 || naturalRoll == 1;
        boolean passed = naturalRoll == 20 || (naturalRoll != 1 && totalRoll >= request.dc());

        return new SkillCheckResult(naturalRoll, -1, RollMode.NORMAL, statModifier, proficiencyBonus, totalRoll, request.dc(), passed, critical);
    }
}
