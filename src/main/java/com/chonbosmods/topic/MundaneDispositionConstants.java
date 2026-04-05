package com.chonbosmods.topic;

/**
 * Named constants for all disposition deltas and probabilities in the
 * simplified mundane dialogue flow. Values are provisional: future formula
 * work will replace them. The separation of concerns is the deliverable.
 */
public final class MundaneDispositionConstants {

    private MundaneDispositionConstants() {}

    // --- Build-time probabilities ---

    /** Chance a stat check is included at all when the pool entry has one. */
    public static final double STAT_CHECK_INCLUSION_CHANCE = 0.60;

    /** Probability of showing exactly 1 additional beat (2 total). */
    public static final double BEAT_COUNT_1_CHANCE = 0.65;

    /** Probability of showing exactly 2 additional beats (3 total). */
    public static final double BEAT_COUNT_2_CHANCE = 0.30;

    // Remaining probability (0.05) shows all 3 additional beats (4 total).

    // --- Disposition deltas ---

    /** Applied when the player listens through an entire mundane topic. */
    public static final int TOPIC_COMPLETED = 1;

    /** Applied when the player passes a stat check during mundane dialogue. */
    public static final int STAT_CHECK_PASS = 3;

    /** Applied when the player fails a stat check during mundane dialogue. */
    public static final int STAT_CHECK_FAIL = -2;

    /** Applied when the player abandons a topic mid-flow. */
    public static final int TOPIC_ABANDONED = 0;
}
