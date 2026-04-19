package com.chonbosmods.quest;

import com.chonbosmods.dialogue.DifficultyTier;

/**
 * Zone-mlvl-scaled tier weight distribution for quest-procedural skill checks (where a quest's
 * SkillCheck has no authored tier). Linear interpolation between low-zone and high-zone anchors.
 * Shifted harder than {@link com.chonbosmods.topic.MundaneTierWeights} at every mlvl: a quest
 * skill check carries more narrative weight than a mundane side-beat.
 */
public final class QuestProceduralWeights {

    private QuestProceduralWeights() {}

    // Indexed by DifficultyTier.ordinal(): TRIVIAL, EASY, MEDIUM, HARD, VERY_HARD, NEARLY_IMPOSSIBLE
    static final double[] LOW_ZONE  = { 0.05, 0.20, 0.50, 0.25, 0.00, 0.00 };
    static final double[] HIGH_ZONE = { 0.00, 0.05, 0.20, 0.35, 0.30, 0.10 };

    public static double[] forMlvl(int mlvl) {
        int clamped = Math.clamp(mlvl, 1, 40);
        double t = (clamped - 1) / 39.0;
        double[] out = new double[DifficultyTier.values().length];
        double sum = 0;
        for (int i = 0; i < out.length; i++) {
            out[i] = LOW_ZONE[i] + (HIGH_ZONE[i] - LOW_ZONE[i]) * t;
            sum += out[i];
        }
        for (int i = 0; i < out.length; i++) out[i] /= sum;
        return out;
    }
}
