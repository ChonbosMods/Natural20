package com.chonbosmods.topic;

import java.util.Random;

/**
 * Determines the framing structure of a topic intro: which combination of
 * tone opener and closer wraps the L0 fragment.
 *
 * <p>Probability weights are disposition-bracket-scaled so that hostile NPCs
 * lean toward openers (gruff lead-ins) while neutral NPCs often go bare.</p>
 */
public enum FramingShape {
    /** No framing: just the L0 fragment. */
    BARE,
    /** Tone opener before L0. */
    OPENER_ONLY,
    /** Tone closer after L0. */
    CLOSER_ONLY,
    /** Full sandwich: opener + L0 + closer. */
    BOTH;

    // Rows: hostile, unfriendly, neutral, friendly, loyal
    // Cols: BARE, OPENER_ONLY, CLOSER_ONLY, BOTH
    private static final int[][] WEIGHTS = {
        { 15, 40, 15, 30 }, // hostile
        { 25, 30, 20, 25 }, // unfriendly
        { 40, 25, 25, 10 }, // neutral
        { 30, 25, 30, 15 }, // friendly
        { 25, 25, 30, 20 }, // loyal
    };

    private static final FramingShape[] VALUES = values();

    /**
     * Roll a framing shape using disposition-scaled probability weights.
     *
     * @param bracket disposition bracket name (hostile, unfriendly, neutral, friendly, loyal)
     * @param random  random source
     * @return the rolled framing shape
     */
    public static FramingShape roll(String bracket, Random random) {
        int row = bracketIndex(bracket);
        int[] w = WEIGHTS[row];
        int roll = random.nextInt(100);

        int cumulative = 0;
        for (int i = 0; i < w.length; i++) {
            cumulative += w[i];
            if (roll < cumulative) {
                return VALUES[i];
            }
        }
        // Should never reach here since weights sum to 100, but guard anyway.
        return BARE;
    }

    public boolean hasOpener() {
        return this == OPENER_ONLY || this == BOTH;
    }

    public boolean hasCloser() {
        return this == CLOSER_ONLY || this == BOTH;
    }

    private static int bracketIndex(String bracket) {
        return switch (bracket) {
            case "hostile" -> 0;
            case "unfriendly" -> 1;
            case "neutral" -> 2;
            case "friendly" -> 3;
            case "loyal" -> 4;
            default -> 2; // neutral fallback
        };
    }
}
