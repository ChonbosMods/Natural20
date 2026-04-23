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
     * Non-negative skill-check modifier: {@code floor(score / 3)}. Score 0→+0,
     * 9→+3, 15→+5, 30→+10. Unified with {@link #getPowerModifier(Stat)} so d20
     * checks and flat combat math use the same stat curve: even a dumped stat
     * contributes a non-negative baseline instead of the D&D 5e signed penalty.
     */
    public int getSkillCheckModifier(Stat stat) {
        return Math.floorDiv(getStat(stat), 3);
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

    /**
     * Proficiency bonus scaled for Natural 20's 40-level progression: 8-level
     * tiers stretch D&D's 20-level curve so the bonus only ticks every 8
     * character levels. Levels 1-8→+2, 9-16→+3, 17-24→+4, 25-32→+5, 33-40→+6.
     */
    public int getProficiencyBonus() {
        return 2 + (level - 1) / 8;
    }
}
