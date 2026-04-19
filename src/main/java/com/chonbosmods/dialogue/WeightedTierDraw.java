package com.chonbosmods.dialogue;

import java.util.Random;

/**
 * Shared helper for sampling a {@link DifficultyTier} from a weight distribution.
 * Used by both mundane ({@code TopicGraphBuilder}) and quest-procedural paths so
 * the sampling logic stays consistent. Weights are indexed by
 * {@code DifficultyTier.ordinal()}.
 */
public final class WeightedTierDraw {

    private WeightedTierDraw() {}

    /**
     * @param weights indexed by DifficultyTier.ordinal(); assumed to sum to 1.0
     *                (or at least be non-negative with positive total).
     */
    public static DifficultyTier pick(double[] weights, Random rng) {
        DifficultyTier[] all = DifficultyTier.values();
        if (weights.length != all.length) {
            throw new IllegalArgumentException(
                    "weights length " + weights.length + " != DifficultyTier count " + all.length);
        }
        double total = 0;
        for (double w : weights) total += w;
        if (total <= 0) {
            throw new IllegalArgumentException("weights must have a positive total; got " + total);
        }
        double roll = rng.nextDouble() * total;
        double cum = 0;
        for (int i = 0; i < all.length; i++) {
            cum += weights[i];
            if (roll < cum) return all[i];
        }
        return all[all.length - 1];
    }
}
