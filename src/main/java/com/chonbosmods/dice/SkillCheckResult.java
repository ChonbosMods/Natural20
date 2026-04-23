package com.chonbosmods.dice;

public record SkillCheckResult(
        int naturalRoll,     // the kept die face used in totalRoll and pass logic
        int otherRoll,       // second die under ADVANTAGE/DISADVANTAGE; -1 under NORMAL
        RollMode mode,       // the mode actually used (for UI rendering)
        int statModifier,
        int proficiencyBonus,
        int totalRoll,
        int dc,
        boolean passed,
        boolean critical
) {}
