package com.chonbosmods.topic;

import com.chonbosmods.dialogue.DifficultyTier;

/**
 * Zone-mlvl-scaled tier weight distribution for mundane (TopicGraphBuilder) stat checks.
 * Linear interpolation between low-zone and high-zone anchors by mlvl in [1, 40].
 * Anchors live here so designers can retune without recompiling logic elsewhere.
 */
public final class MundaneTierWeights {

    private MundaneTierWeights() {}

    // Indexed by DifficultyTier.ordinal()
    static final double[] LOW_ZONE  = { 0.15, 0.30, 0.40, 0.15, 0.00, 0.00 };
    static final double[] HIGH_ZONE = { 0.00, 0.10, 0.30, 0.35, 0.20, 0.05 };

    public static double[] forMlvl(int mlvl) {
        int clamped = Math.clamp(mlvl, 1, 40);
        double t = (clamped - 1) / 39.0;
        double[] out = new double[DifficultyTier.values().length];
        double sum = 0;
        for (int i = 0; i < out.length; i++) {
            out[i] = LOW_ZONE[i] + (HIGH_ZONE[i] - LOW_ZONE[i]) * t;
            sum += out[i];
        }
        // Normalize (guard against floating-point drift; anchors already sum to 1.0).
        for (int i = 0; i < out.length; i++) out[i] /= sum;
        return out;
    }
}
