package com.chonbosmods.dialogue;

import java.util.*;

/**
 * Stateful calculator for valence-related computations during a conversation.
 * Zero external dependencies: constructable with primitives, testable without mocks.
 * ConversationSession holds one per conversation, feeds events, queries results.
 */
public class ValenceTracker {

    /** Alignment multipliers: [valence ordinal][posture index] */
    private static final double[][] ALIGNMENT_MATRIX = {
        // POSITIVE:  attentive, cheerful, engaged, direct, skeptical, dismissive
        {1.0, 1.5, 1.0, 1.0, 1.5, 1.0},
        // NEGATIVE:  attentive, cheerful, engaged, direct, skeptical, dismissive
        {1.5, 1.0, 1.0, 1.0, 1.0, 1.5},
        // NEUTRAL:   attentive, cheerful, engaged, direct, skeptical, dismissive
        {1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
    };

    private static final List<String> POSTURE_GROUPS = List.of(
        "attentive", "cheerful", "engaged", "direct", "skeptical", "dismissive"
    );

    private static final int DISPOSITION_DELTA_CLAMP = 5;

    private final List<ValenceType> recentValences;
    private int trajectoryScore;

    /** New conversation, no history. */
    public ValenceTracker() {
        this.recentValences = new ArrayList<>();
        this.trajectoryScore = 0;
    }

    /** Returning conversation: seed momentum with opening valence from drift. */
    public ValenceTracker(ValenceType openingValence) {
        this.recentValences = new ArrayList<>();
        if (openingValence != null) {
            this.recentValences.add(openingValence);
        }
        this.trajectoryScore = 0;
    }

    /** Restore from saved state. */
    private ValenceTracker(List<ValenceType> recentValences, int trajectoryScore) {
        this.recentValences = new ArrayList<>(recentValences);
        this.trajectoryScore = trajectoryScore;
    }

    // --- Event Methods ---

    /** Record that an NPC line with the given valence was delivered to the player. */
    public void recordNpcLine(ValenceType valence) {
        recentValences.add(valence);
        switch (valence) {
            case POSITIVE -> trajectoryScore++;
            case NEGATIVE -> trajectoryScore--;
            case NEUTRAL -> {}
        }
    }

    // --- Query Methods (Pure Reads) ---

    /** Valence of the most recently recorded NPC line, or NEUTRAL if none. */
    public ValenceType getCurrentValence() {
        if (recentValences.isEmpty()) return ValenceType.NEUTRAL;
        return recentValences.getLast();
    }

    /** True if at least one NPC line has been recorded. */
    public boolean hasRecordedLines() {
        return !recentValences.isEmpty();
    }

    /**
     * Mode of the last 3 NPC line valences. Returns null if ambiguous (all different).
     * Used for momentum: bias kicks in when 2+ of last 3 lines share a valence.
     */
    public ValenceType getDominantValence() {
        int size = recentValences.size();
        if (size == 0) return null;

        int window = Math.min(3, size);
        Map<ValenceType, Integer> counts = new EnumMap<>(ValenceType.class);
        for (int i = size - window; i < size; i++) {
            counts.merge(recentValences.get(i), 1, Integer::sum);
        }

        ValenceType best = null;
        int bestCount = 0;
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }

        // Ambiguous if all different (window=3, bestCount=1) or tie
        if (bestCount < 2 && window >= 3) return null;
        return best;
    }

    /**
     * Per-valence weight multipliers for topic momentum.
     * Same-as-dominant gets 1.5x, opposite non-neutral gets 0.7x, neutral is never penalized.
     * Returns empty map if no dominant valence (no bias applied).
     */
    public Map<ValenceType, Double> getMomentumWeights() {
        ValenceType dominant = getDominantValence();
        if (dominant == null) return Map.of();

        Map<ValenceType, Double> weights = new EnumMap<>(ValenceType.class);
        for (ValenceType v : ValenceType.values()) {
            if (v == dominant) {
                weights.put(v, 1.5);
            } else if (v == ValenceType.NEUTRAL) {
                weights.put(v, 1.0);
            } else {
                weights.put(v, 0.7);
            }
        }
        return weights;
    }

    /** Raw trajectory score for quest hook weighting. */
    public int getTrajectoryScore() {
        return trajectoryScore;
    }

    /**
     * Compute disposition delta from a posture selection.
     * Applies alignment matrix multiplier, rounds toward zero, clamps to [-5, +5].
     *
     * @param baseModifier the posture group's base disposition modifier
     * @param postureGroup the selected posture group name (e.g., "attentive", "cheerful")
     * @return the adjusted disposition delta
     */
    public int computeDispositionDelta(int baseModifier, String postureGroup) {
        ValenceType valence = getCurrentValence();
        int postureIndex = POSTURE_GROUPS.indexOf(postureGroup);
        if (postureIndex < 0) return baseModifier;

        double multiplier = ALIGNMENT_MATRIX[valence.ordinal()][postureIndex];
        double raw = baseModifier * multiplier;

        // Round toward zero (truncate)
        int delta = (int) raw;

        // Clamp
        return Math.clamp(delta, -DISPOSITION_DELTA_CLAMP, DISPOSITION_DELTA_CLAMP);
    }

    // --- Static Pure Functions ---

    /**
     * Evaluate valence drift between conversations.
     * Takes stored closing valence and a 0.0-1.0 roll, returns the opening valence.
     * Drift can only move one step: positive <-> neutral <-> negative.
     */
    public static ValenceType evaluateDrift(ValenceType storedClosing, double roll) {
        if (storedClosing == null) return ValenceType.NEUTRAL;
        return switch (storedClosing) {
            case POSITIVE -> roll < 0.25 ? ValenceType.NEUTRAL : ValenceType.POSITIVE;
            case NEGATIVE -> roll < 0.25 ? ValenceType.NEUTRAL : ValenceType.NEGATIVE;
            case NEUTRAL -> {
                if (roll < 0.15) yield ValenceType.POSITIVE;
                if (roll < 0.30) yield ValenceType.NEGATIVE;
                yield ValenceType.NEUTRAL;
            }
        };
    }

    // --- Serialization ---

    public record State(List<ValenceType> recentValences, int trajectoryScore) {}

    public State getState() {
        return new State(List.copyOf(recentValences), trajectoryScore);
    }

    public static ValenceTracker fromState(State state) {
        return new ValenceTracker(state.recentValences(), state.trajectoryScore());
    }
}
