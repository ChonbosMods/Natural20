package com.chonbosmods.loot.mob.naming;

/**
 * Rarity tiers for elite mob name generation.
 * Used internally by Nat20MobNameGenerator for name-pool filtering.
 * Nat20MobNameGenerator.rarityFor(DifficultyTier) maps DifficultyTier values to these rarities.
 */
public enum MobNameRarity {
    UNCOMMON(1),
    RARE(2),
    EPIC(3),
    LEGENDARY(4);

    private final int rank;

    MobNameRarity(int rank) {
        this.rank = rank;
    }

    /** Numeric rank used for pool filtering: a word is eligible if its minRarity.rank() <= the mob's rarity.rank(). */
    public int rank() {
        return rank;
    }

    /**
     * Parse a rarity from a string (case-insensitive).
     * Returns UNCOMMON if no match is found.
     */
    public static MobNameRarity fromString(String s) {
        for (MobNameRarity r : values()) {
            if (r.name().equalsIgnoreCase(s)) {
                return r;
            }
        }
        return UNCOMMON;
    }

    /**
     * Map a tier ordinal to a naming rarity.
     * Ordinal 0 should never be called: those mobs get no name.
     * 1=UNCOMMON, 2=RARE, 3=EPIC, 4=LEGENDARY; anything else defaults to UNCOMMON.
     * Note: currently unused (name generator uses switch-based rarityFor(DifficultyTier) instead). Candidate for future deletion.
     */
    public static MobNameRarity fromTierOrdinal(int ordinal) {
        return switch (ordinal) {
            case 1 -> UNCOMMON;
            case 2 -> RARE;
            case 3 -> EPIC;
            case 4 -> LEGENDARY;
            default -> UNCOMMON;
        };
    }
}
