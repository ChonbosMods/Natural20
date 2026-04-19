package com.chonbosmods.topic;

import com.chonbosmods.dialogue.DifficultyTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MundaneTierWeightsTest {

    @Test
    void lowZoneAnchor_matchesDesign() {
        double[] w = MundaneTierWeights.forMlvl(1);
        assertEquals(0.15, w[DifficultyTier.TRIVIAL.ordinal()], 1e-9);
        assertEquals(0.30, w[DifficultyTier.EASY.ordinal()], 1e-9);
        assertEquals(0.40, w[DifficultyTier.MEDIUM.ordinal()], 1e-9);
        assertEquals(0.15, w[DifficultyTier.HARD.ordinal()], 1e-9);
        assertEquals(0.0,  w[DifficultyTier.VERY_HARD.ordinal()], 1e-9);
        assertEquals(0.0,  w[DifficultyTier.NEARLY_IMPOSSIBLE.ordinal()], 1e-9);
    }

    @Test
    void highZoneAnchor_matchesDesign() {
        double[] w = MundaneTierWeights.forMlvl(40);
        assertEquals(0.0,  w[DifficultyTier.TRIVIAL.ordinal()], 1e-9);
        assertEquals(0.10, w[DifficultyTier.EASY.ordinal()], 1e-9);
        assertEquals(0.30, w[DifficultyTier.MEDIUM.ordinal()], 1e-9);
        assertEquals(0.35, w[DifficultyTier.HARD.ordinal()], 1e-9);
        assertEquals(0.20, w[DifficultyTier.VERY_HARD.ordinal()], 1e-9);
        assertEquals(0.05, w[DifficultyTier.NEARLY_IMPOSSIBLE.ordinal()], 1e-9);
    }

    @Test
    void weights_sumToOneAtEveryMlvl() {
        for (int mlvl = 1; mlvl <= 40; mlvl++) {
            double sum = 0;
            for (double w : MundaneTierWeights.forMlvl(mlvl)) sum += w;
            assertEquals(1.0, sum, 1e-6, "mlvl=" + mlvl);
        }
    }

    @Test
    void weights_clampMlvlOutOfRange() {
        assertArrayEquals(MundaneTierWeights.forMlvl(1), MundaneTierWeights.forMlvl(-5), 1e-9);
        assertArrayEquals(MundaneTierWeights.forMlvl(40), MundaneTierWeights.forMlvl(100), 1e-9);
    }

    @Test
    void midMlvl_interpolatesLinearly() {
        // mlvl 20 is roughly midway: each tier's weight should fall between endpoints.
        double[] low = MundaneTierWeights.forMlvl(1);
        double[] mid = MundaneTierWeights.forMlvl(20);
        double[] high = MundaneTierWeights.forMlvl(40);
        for (DifficultyTier t : DifficultyTier.values()) {
            double lo = Math.min(low[t.ordinal()], high[t.ordinal()]);
            double hi = Math.max(low[t.ordinal()], high[t.ordinal()]);
            assertTrue(mid[t.ordinal()] >= lo && mid[t.ordinal()] <= hi,
                    "tier=" + t + " mid=" + mid[t.ordinal()]);
        }
    }
}
