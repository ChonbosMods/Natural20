package com.chonbosmods.loot.mob;

public enum EncounterTier {
    NORMAL(0, 1.0f),
    ENHANCED(1, 1.3f),
    ELITE(2, 1.6f),
    CHAMPION(3, 2.0f),
    BOSS(4, 3.0f);

    private final int maxMobAffixes;
    private final float statMultiplier;

    EncounterTier(int maxMobAffixes, float statMultiplier) {
        this.maxMobAffixes = maxMobAffixes;
        this.statMultiplier = statMultiplier;
    }

    /** Maximum number of mob affixes that can be rolled for this tier. */
    public int maxMobAffixes() {
        return maxMobAffixes;
    }

    /** Base stat multiplier applied to the mob's stats. */
    public float statMultiplier() {
        return statMultiplier;
    }

    /**
     * Look up an EncounterTier by its ordinal value.
     * Returns NORMAL if the ordinal is out of range.
     */
    public static EncounterTier fromOrdinal(int ordinal) {
        EncounterTier[] tiers = values();
        if (ordinal >= 0 && ordinal < tiers.length) {
            return tiers[ordinal];
        }
        return NORMAL;
    }

    /**
     * Look up an EncounterTier by name (case-insensitive).
     * Returns NORMAL if no match is found.
     */
    public static EncounterTier fromName(String name) {
        for (EncounterTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return NORMAL;
    }
}
