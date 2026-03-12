package com.chonbosmods.dice;

public record SkillCheckResult(
        int naturalRoll,
        int statModifier,
        int proficiencyBonus,
        int totalRoll,
        int dc,
        boolean passed,
        boolean critical
) {}
