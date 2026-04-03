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

    /** Probability that a topic has no framing (opener/closer) at all. */
    private static final double BARE_PROBABILITY = 0.75;

    /** Opener bias per disposition bracket: higher = more likely to pick opener over closer. */
    private static final double OPENER_BIAS_HOSTILE = 0.80;
    private static final double OPENER_BIAS_UNFRIENDLY = 0.65;
    private static final double OPENER_BIAS_NEUTRAL = 0.50;
    private static final double OPENER_BIAS_FRIENDLY = 0.35;
    private static final double OPENER_BIAS_LOYAL = 0.20;

    /** Disposition bracket name constants (must match DispositionBracket.textPoolFromDisposition output). */
    private static final String BRACKET_HOSTILE = "hostile";
    private static final String BRACKET_UNFRIENDLY = "unfriendly";
    private static final String BRACKET_NEUTRAL = "neutral";
    private static final String BRACKET_FRIENDLY = "friendly";
    private static final String BRACKET_LOYAL = "loyal";

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
        if (random.nextDouble() < BARE_PROBABILITY) return BARE;

        // Remaining chance: pick opener or closer based on disposition bracket.
        // Hostile NPCs lean toward openers, friendly toward closers.
        double openerBias = switch (bracket) {
            case BRACKET_HOSTILE -> OPENER_BIAS_HOSTILE;
            case BRACKET_UNFRIENDLY -> OPENER_BIAS_UNFRIENDLY;
            case BRACKET_NEUTRAL -> OPENER_BIAS_NEUTRAL;
            case BRACKET_FRIENDLY -> OPENER_BIAS_FRIENDLY;
            case BRACKET_LOYAL -> OPENER_BIAS_LOYAL;
            default -> OPENER_BIAS_NEUTRAL;
        };

        return random.nextDouble() < openerBias ? OPENER_ONLY : CLOSER_ONLY;
    }
}
