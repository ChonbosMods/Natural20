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
        if (clamped < 20) return "hostile";
        if (clamped < 40) return "unfriendly";
        if (clamped < 60) return "neutral";
        if (clamped < 80) return "friendly";
        return "loyal";
    }

    public static int effectiveDC(int baseDC, int disposition, boolean dispositionScaling) {
        if (!dispositionScaling) return baseDC;
        int modifier = fromDisposition(disposition).dcModifier;
        return Math.clamp(baseDC + modifier, 1, 30);
    }

    public static int clampDisposition(int value) {
        return Math.clamp(value, 0, 100);
    }
}
