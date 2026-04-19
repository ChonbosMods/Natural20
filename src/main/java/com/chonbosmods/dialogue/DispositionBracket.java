package com.chonbosmods.dialogue;

import com.chonbosmods.dice.RollMode;

/**
 * Disposition label + roll-mode lookup.
 *
 * <p>The 9-bracket enum (HOSTILE..LOYAL) is UI decoration only: it drives the
 * disposition label and color shown on {@code Nat20DialoguePage}. The gameplay
 * mechanic is the 3-band {@link #rollMode(int)} split (hostile/neutral/friendly),
 * which controls advantage/disadvantage on dialogue skill checks.
 *
 * <p>The two are intentionally separate: fine-grained UI labels vs. coarse
 * gameplay effect. Do not re-couple them to a DC-shift table.
 */
public enum DispositionBracket {
    HOSTILE(0, 10),
    SCORNFUL(11, 24),
    UNFRIENDLY(25, 39),
    WARY(40, 49),
    NEUTRAL(50, 59),
    CORDIAL(60, 69),
    FRIENDLY(70, 79),
    TRUSTED(80, 89),
    LOYAL(90, 100);

    // --- Text pool boundary thresholds (used in textPoolFromDisposition) ---

    /** Disposition below this value maps to "unfriendly" text pool (below = "hostile"). */
    private static final int TEXT_POOL_UNFRIENDLY_MIN = 20;
    /** Disposition below this value maps to "neutral" text pool. */
    private static final int TEXT_POOL_NEUTRAL_MIN = 40;
    /** Disposition below this value maps to "friendly" text pool. */
    private static final int TEXT_POOL_FRIENDLY_MIN = 60;
    /** Disposition below this value maps to "loyal" text pool. */
    private static final int TEXT_POOL_LOYAL_MIN = 80;

    // --- Roll-mode band thresholds (used in rollMode) ---

    /** Disposition at or above this value rolls at NORMAL (below = DISADVANTAGE). */
    private static final int NEUTRAL_MIN = 25;
    /** Disposition at or above this value rolls at ADVANTAGE. */
    private static final int FRIENDLY_MIN = 75;

    private final int minDisposition;
    private final int maxDisposition;

    DispositionBracket(int min, int max) {
        this.minDisposition = min;
        this.maxDisposition = max;
    }

    public static DispositionBracket fromDisposition(int disposition) {
        int clamped = Math.clamp(disposition, 0, 100);
        for (DispositionBracket bracket : values()) {
            if (clamped >= bracket.minDisposition && clamped <= bracket.maxDisposition) {
                return bracket;
            }
        }
        return NEUTRAL;
    }

    /**
     * Maps a disposition value to one of 5 text pool keys using even 20-point blocks.
     * Returns: "hostile" (0-19), "unfriendly" (20-39), "neutral" (40-59),
     *          "friendly" (60-79), "loyal" (80-100).
     */
    public static String textPoolFromDisposition(int disposition) {
        int clamped = Math.clamp(disposition, 0, 100);
        if (clamped < TEXT_POOL_UNFRIENDLY_MIN) return "hostile";
        if (clamped < TEXT_POOL_NEUTRAL_MIN) return "unfriendly";
        if (clamped < TEXT_POOL_FRIENDLY_MIN) return "neutral";
        if (clamped < TEXT_POOL_LOYAL_MIN) return "friendly";
        return "loyal";
    }

    /**
     * Maps a disposition value to the d20 roll mode for dialogue skill checks.
     *
     * <p>Three bands (clamped to 0..100):
     * <ul>
     *   <li>0-24: {@link RollMode#DISADVANTAGE}</li>
     *   <li>25-74: {@link RollMode#NORMAL}</li>
     *   <li>75-100: {@link RollMode#ADVANTAGE}</li>
     * </ul>
     *
     * <p>Kept separate from the 9-bracket enum on purpose: the enum is UI
     * decoration (labels/colors); this is the gameplay mechanic.
     */
    public static RollMode rollMode(int disposition) {
        int clamped = Math.clamp(disposition, 0, 100);
        if (clamped < NEUTRAL_MIN) return RollMode.DISADVANTAGE;
        if (clamped < FRIENDLY_MIN) return RollMode.NORMAL;
        return RollMode.ADVANTAGE;
    }

    public static int clampDisposition(int value) {
        return Math.clamp(value, 0, 100);
    }
}
