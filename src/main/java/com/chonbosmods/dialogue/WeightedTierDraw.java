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
        double total = 0;
        for (double w : weights) total += w;
        if (total <= 0) return DifficultyTier.MEDIUM;  // defensive fallback
        double roll = rng.nextDouble() * total;
        double cum = 0;
        DifficultyTier[] all = DifficultyTier.values();
        for (int i = 0; i < all.length; i++) {
            cum += weights[i];
            if (roll < cum) return all[i];
        }
        return all[all.length - 1];
    }
}
