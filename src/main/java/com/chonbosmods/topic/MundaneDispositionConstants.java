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

    /** Per-beat chance the stat check first appears (if approved at build time). */
    public static final double STAT_CHECK_PER_BEAT_CHANCE = 0.30;

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
