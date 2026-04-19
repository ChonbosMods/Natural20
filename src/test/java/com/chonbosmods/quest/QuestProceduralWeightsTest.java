package com.chonbosmods.quest;

import com.chonbosmods.dialogue.DifficultyTier;
import com.chonbosmods.topic.MundaneTierWeights;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestProceduralWeightsTest {

    @Test
    void lowZoneAnchor_matchesDesign() {
        double[] w = QuestProceduralWeights.forMlvl(1);
        assertEquals(0.05, w[DifficultyTier.TRIVIAL.ordinal()], 1e-9);
        assertEquals(0.20, w[DifficultyTier.EASY.ordinal()], 1e-9);
        assertEquals(0.50, w[DifficultyTier.MEDIUM.ordinal()], 1e-9);
        assertEquals(0.25, w[DifficultyTier.HARD.ordinal()], 1e-9);
        assertEquals(0.0,  w[DifficultyTier.VERY_HARD.ordinal()], 1e-9);
        assertEquals(0.0,  w[DifficultyTier.NEARLY_IMPOSSIBLE.ordinal()], 1e-9);
    }

    @Test
    void highZoneAnchor_matchesDesign() {
        double[] w = QuestProceduralWeights.forMlvl(40);
        assertEquals(0.0,  w[DifficultyTier.TRIVIAL.ordinal()], 1e-9);
        assertEquals(0.05, w[DifficultyTier.EASY.ordinal()], 1e-9);
        assertEquals(0.20, w[DifficultyTier.MEDIUM.ordinal()], 1e-9);
        assertEquals(0.35, w[DifficultyTier.HARD.ordinal()], 1e-9);
        assertEquals(0.30, w[DifficultyTier.VERY_HARD.ordinal()], 1e-9);
        assertEquals(0.10, w[DifficultyTier.NEARLY_IMPOSSIBLE.ordinal()], 1e-9);
    }

    @Test
    void weights_sumToOneAtEveryMlvl() {
        for (int mlvl = 1; mlvl <= 40; mlvl++) {
            double sum = 0;
            for (double w : QuestProceduralWeights.forMlvl(mlvl)) sum += w;
            assertEquals(1.0, sum, 1e-6, "mlvl=" + mlvl);
        }
    }

    @Test
    void questHarderThanMundane_atEveryMlvl() {
        // Expected-value DC comparison: quest avg DC >= mundane avg DC at every mlvl.
        for (int mlvl = 1; mlvl <= 40; mlvl++) {
            double[] mun = MundaneTierWeights.forMlvl(mlvl);
            double[] qst = QuestProceduralWeights.forMlvl(mlvl);
            double mundaneEv = expectedDc(mun);
            double questEv = expectedDc(qst);
            assertTrue(questEv >= mundaneEv,
                    "mlvl=" + mlvl + " mundane=" + mundaneEv + " quest=" + questEv);
        }
    }

    private static double expectedDc(double[] weights) {
        double sum = 0;
        for (DifficultyTier t : DifficultyTier.values()) {
            sum += weights[t.ordinal()] * t.dc();
        }
        return sum;
    }
}
