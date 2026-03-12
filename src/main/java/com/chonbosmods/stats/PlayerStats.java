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

    public int getModifier(Stat stat) {
        return Math.floorDiv(getStat(stat) - 10, 2);
    }

    public boolean isProficient(Skill skill) {
        return proficiencies.contains(skill.name());
    }

    public int getProficiencyBonus() {
        return 2 + (level - 1) / 4;
    }
}
