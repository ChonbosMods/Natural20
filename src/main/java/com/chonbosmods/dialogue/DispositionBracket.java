package com.chonbosmods.dialogue;

public enum DispositionBracket {
    HOSTILE(0, 10, 5),
    SCORNFUL(11, 24, 3),
    UNFRIENDLY(25, 39, 2),
    WARY(40, 49, 1),
    NEUTRAL(50, 59, 0),
    CORDIAL(60, 69, -1),
    FRIENDLY(70, 79, -2),
    TRUSTED(80, 89, -3),
    LOYAL(90, 100, -4);

    // --- Text pool boundary thresholds (used in textPoolFromDisposition) ---

    /** Disposition below this value maps to "unfriendly" text pool (below = "hostile"). */
    private static final int TEXT_POOL_UNFRIENDLY_MIN = 20;
    /** Disposition below this value maps to "neutral" text pool. */
    private static final int TEXT_POOL_NEUTRAL_MIN = 40;
    /** Disposition below this value maps to "friendly" text pool. */
    private static final int TEXT_POOL_FRIENDLY_MIN = 60;
    /** Disposition below this value maps to "loyal" text pool. */
    private static final int TEXT_POOL_LOYAL_MIN = 80;

    /** Maximum effective DC after disposition modifier is applied. */
    private static final int MAX_EFFECTIVE_DC = 30;

    private final int minDisposition;
    private final int maxDisposition;
    private final int dcModifier;

    DispositionBracket(int min, int max, int dcMod) {
        this.minDisposition = min;
        this.maxDisposition = max;
        this.dcModifier = dcMod;
    }

    public int dcModifier() {
        return dcModifier;
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

    public static int effectiveDC(int baseDC, int disposition, boolean dispositionScaling) {
        if (!dispositionScaling) return baseDC;
        int modifier = fromDisposition(disposition).dcModifier;
        return Math.clamp(baseDC + modifier, 1, MAX_EFFECTIVE_DC);
    }

    public static int clampDisposition(int value) {
        return Math.clamp(value, 0, 100);
    }
}
