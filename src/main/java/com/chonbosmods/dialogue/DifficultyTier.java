package com.chonbosmods.dialogue;

/**
 * Canonical difficulty tiers for d20 skill checks. Each tier maps to a
 * fixed base DC aligned with D&D 5e's difficulty table. Authors pick a tier
 * rather than a raw number so checks stay comparable across the codebase.
 */
public enum DifficultyTier {
    TRIVIAL(5),
    EASY(10),
    MEDIUM(15),
    HARD(20),
    VERY_HARD(25),
    NEARLY_IMPOSSIBLE(30);

    private final int dc;

    DifficultyTier(int dc) {
        this.dc = dc;
    }

    public int dc() {
        return dc;
    }
}
