package com.chonbosmods.progression;

import java.util.Random;

/**
 * Pure math helpers for the Natural 20 XP + mlvl system.
 * See docs/plans/2026-04-15-xp-mlvl-ilvl-system-design.md for derivation.
 */
public final class Nat20XpMath {

    public static final int    XP_BASE         = 909;
    public static final double XP_GROWTH       = 1.12;
    public static final int    XP_UNIT         = 15;
    public static final int    LEVEL_CAP       = 40;
    public static final int    LEVELS_PER_ZONE = 10;
    public static final int    TOTAL_ZONES     = 4;

    private Nat20XpMath() {}

    /** XP required to advance from {@code level} to {@code level + 1}. */
    public static int xpToNextLevel(int level) {
        return (int) Math.floor(XP_BASE * Math.pow(XP_GROWTH, level - 1));
    }

    /** Cumulative XP required to reach {@code level} from level 1. */
    public static long cumulativeXp(int level) {
        long total = 0;
        for (int l = 1; l < level; l++) {
            total += xpToNextLevel(l);
        }
        return total;
    }

    /** Derive the current level from cumulative XP. Caps at LEVEL_CAP. */
    public static int levelForTotalXp(long totalXp) {
        if (totalXp <= 0) return 1;
        long running = 0;
        for (int l = 1; l < LEVEL_CAP; l++) {
            running += xpToNextLevel(l);
            if (totalXp < running) return l;
        }
        return LEVEL_CAP;
    }

    /** Zone containing {@code level} (1-indexed). */
    public static int zoneForLevel(int level) {
        if (level <= 10) return 1;
        if (level <= 20) return 2;
        if (level <= 30) return 3;
        return 4;
    }

    /** Zone scale factor at the start of a zone. */
    public static double zoneScale(int zone) {
        return Math.pow(XP_GROWTH, (zone - 1) * LEVELS_PER_ZONE);
    }

    /** Smooth content-scaling factor at a specific level. Interpolates across zone boundaries. */
    public static double smoothScale(int level) {
        int zone = zoneForLevel(level);
        double currentScale = zoneScale(zone);
        int zoneStart = (zone - 1) * LEVELS_PER_ZONE + 1;
        double progress = (level - zoneStart) / (double) LEVELS_PER_ZONE;
        double nextScale = zone < TOTAL_ZONES ? zoneScale(zone + 1) : currentScale * 1.3;
        return currentScale * (1 + progress * (nextScale / currentScale - 1));
    }

    /** XP awarded for killing a mob at {@code mlvl} with the given tier weight (units of U). */
    public static int mobKillXp(int mlvl, double tierWeight) {
        return (int) Math.floor(tierWeight * XP_UNIT * smoothScale(mlvl));
    }

    /** XP for completing one quest phase, with the player's current level. */
    public static int questPhaseXp(int level) {
        return (int) Math.floor(7.0 * XP_UNIT * smoothScale(level));
    }

    /** XP for a successful D20 skill check, with the player's current level. */
    public static int d20SuccessXp(int level) {
        return (int) Math.floor(10.0 * XP_UNIT * smoothScale(level));
    }

    /**
     * ilvl -> affix-value scale multiplier, per spec §3.2 of the ilvl/mlvl doc.
     * @param ilvl item level (1..45)
     * @param qualityValue rarity tier (Common=1 .. Legendary=5)
     */
    public static double ilvlScale(int ilvl, int qualityValue) {
        double perIlvl = 0.025 + (qualityValue - 1) * 0.003;
        return 1.0 + (ilvl - 1) * perIlvl;
    }

    /** Per-tier integer bonus range for COLLECT_RESOURCES count scaling.
     *  Indexed by tier (1..4). Ranges are hand-tuned small integer intervals
     *  straddling the scaled midpoint {@code (5 - tier) * 1.75}: ±1 for T1/T2/T4
     *  (T4 clamps low at 1), and two adjacent integers {3, 4} for T3 because
     *  its midpoint (3.5) is half-integer. See
     *  {@code docs/plans/2026-04-19-collect-resources-tier-naming-design.md}
     *  for the authoritative derivation. {@link #rollBonusPerZone} rolls a
     *  uniform integer inside the tier's range, once per objective, for
     *  {@code QuestGenerator} to persist on {@code ObjectiveInstance}. */
    private static final int[][] BONUS_RANGE_PER_TIER = {
        null,
        {6, 8},
        {4, 6},
        {3, 4},
        {1, 3},
    };

    /** Roll a per-zone additive bonus for a COLLECT_RESOURCES objective of the
     *  given item tier. Tiers outside {@code [1, 4]} clamp to the nearest valid
     *  bucket so a malformed JSON entry never NPEs at generation time.
     *  @param tier the collect-resource item's tier (1..4), NOT a player zone.
     *              The "BonusPerZone" name describes the unit of the returned
     *              int: the bonus applied for each player zone above 1. */
    public static int rollBonusPerZone(int tier, Random random) {
        int t = Math.max(1, Math.min(4, tier));
        int[] range = BONUS_RANGE_PER_TIER[t];
        return range[0] + random.nextInt(range[1] - range[0] + 1);
    }
}
