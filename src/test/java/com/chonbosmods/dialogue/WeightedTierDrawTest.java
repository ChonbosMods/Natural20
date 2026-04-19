package com.chonbosmods.dialogue;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedTierDrawTest {

    @Test
    void deterministicWithSeededRng() {
        double[] w = { 0.15, 0.30, 0.40, 0.15, 0.00, 0.00 };
        Random a = new Random(42L);
        Random b = new Random(42L);
        assertEquals(WeightedTierDraw.pick(w, a), WeightedTierDraw.pick(w, b));
    }

    @Test
    void neverReturnsZeroWeightTier() {
        double[] w = { 0.15, 0.30, 0.40, 0.15, 0.00, 0.00 };  // top two zero
        Random rng = new Random(1L);
        for (int i = 0; i < 1000; i++) {
            DifficultyTier t = WeightedTierDraw.pick(w, rng);
            assertNotEquals(DifficultyTier.VERY_HARD, t);
            assertNotEquals(DifficultyTier.NEARLY_IMPOSSIBLE, t);
        }
    }

    @Test
    void distributionRoughlyMatchesWeights() {
        double[] w = { 0.0, 0.0, 1.0, 0.0, 0.0, 0.0 };  // all MEDIUM
        Random rng = new Random(1L);
        for (int i = 0; i < 100; i++) {
            assertEquals(DifficultyTier.MEDIUM, WeightedTierDraw.pick(w, rng));
        }
    }
}
