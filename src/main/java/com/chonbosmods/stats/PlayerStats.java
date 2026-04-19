package com.chonbosmods.stats;

import com.chonbosmods.data.Nat20PlayerData;

import java.util.Set;

public record PlayerStats(int[] stats, int level, Set<String> proficiencies) {

    public static PlayerStats from(Nat20PlayerData data) {
        return new PlayerStats(data.getStats().clone(), data.getLevel(), Set.copyOf(data.getProficiencies()));
    }

    public int getStat(Stat stat) {
        return stats[stat.index()];
    }

    /**
     * D&D 5e signed ability modifier: {@code floor((score - 10) / 2)}.
     * Used only for d20 rolls against a difficulty class. Negative values are
     * intentional here: they preserve the "low-stat character fails the roll"
     * texture for dialogue skill checks.
     */
    public int getSkillCheckModifier(Stat stat) {
        return Math.floorDiv(getStat(stat) - 10, 2);
    }

    /**
     * Non-negative power modifier: {@code floor(score / 3)}. Score 0→+0,
     * 9→+3, 15→+5, 30→+10. Used for all flat combat math and gear-affix
     * scaling so that even dumped stats feel capable at baseline.
     */
    public int getPowerModifier(Stat stat) {
        return Math.floorDiv(getStat(stat), 3);
    }

    public boolean isProficient(Skill skill) {
        return proficiencies.contains(skill.name());
    }

    public int getProficiencyBonus() {
        return 2 + (level - 1) / 4;
    }
}
