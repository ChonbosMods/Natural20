package com.chonbosmods.dialogue;

public enum DispositionBracket {
    HOSTILE(0, 24, 4),
    UNFRIENDLY(25, 44, 2),
    NEUTRAL(45, 59, 0),
    FRIENDLY(60, 79, -2),
    ALLIED(80, 100, -4);

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

    public static int effectiveDC(int baseDC, int disposition, boolean dispositionScaling) {
        if (!dispositionScaling) return baseDC;
        int modifier = fromDisposition(disposition).dcModifier;
        return Math.clamp(baseDC + modifier, 1, 30);
    }

    public static int clampDisposition(int value) {
        return Math.clamp(value, 0, 100);
    }
}
