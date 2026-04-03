package com.chonbosmods.topic;

import java.util.Random;

/**
 * Controls which tone framing elements (opener, closer) appear in a topic's dialogue.
 * Quest bearers always use BARE (no framing), while normal topics roll based on
 * disposition bracket probabilities.
 */
public enum FramingShape {
    BARE(false, false),
    OPENER_ONLY(true, false),
    CLOSER_ONLY(false, true),
    BOTH(true, true);

    private final boolean opener;
    private final boolean closer;

    FramingShape(boolean opener, boolean closer) {
        this.opener = opener;
        this.closer = closer;
    }

    public boolean hasOpener() { return opener; }
    public boolean hasCloser() { return closer; }

    /**
     * Roll a framing shape: 75% bare, 25% either opener or closer (never both).
     * When framing appears, disposition bracket biases toward opener or closer.
     */
    public static FramingShape roll(String bracket, Random random) {
        if (random.nextDouble() < 0.75) return BARE;

        // 25% of the time: pick opener or closer based on disposition bracket.
        // Hostile NPCs lean toward openers, friendly toward closers.
        double openerBias = switch (bracket) {
            case "hostile" -> 0.80;
            case "unfriendly" -> 0.65;
            case "neutral" -> 0.50;
            case "friendly" -> 0.35;
            case "loyal" -> 0.20;
            default -> 0.50;
        };

        return random.nextDouble() < openerBias ? OPENER_ONLY : CLOSER_ONLY;
    }
}
