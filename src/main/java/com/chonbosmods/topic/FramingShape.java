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
     * Roll a framing shape based on disposition bracket.
     * Hostile NPCs lean toward openers, friendly toward closers.
     */
    public static FramingShape roll(String bracket, Random random) {
        double openerChance = switch (bracket) {
            case "hostile" -> 0.85;
            case "unfriendly" -> 0.70;
            case "neutral" -> 0.50;
            case "friendly" -> 0.65;
            case "loyal" -> 0.60;
            default -> 0.50;
        };
        double closerChance = 0.6;

        boolean hasOpener = random.nextDouble() < openerChance;
        boolean hasCloser = hasOpener ? (random.nextDouble() < closerChance) : true;

        if (hasOpener && hasCloser) return BOTH;
        if (hasOpener) return OPENER_ONLY;
        if (hasCloser) return CLOSER_ONLY;
        return BARE;
    }
}
