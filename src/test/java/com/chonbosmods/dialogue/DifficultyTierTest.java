package com.chonbosmods.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DifficultyTierTest {

    @Test
    void trivialDcIs5() {
        assertEquals(5, DifficultyTier.TRIVIAL.dc());
    }

    @Test
    void easyDcIs10() {
        assertEquals(10, DifficultyTier.EASY.dc());
    }

    @Test
    void mediumDcIs15() {
        assertEquals(15, DifficultyTier.MEDIUM.dc());
    }

    @Test
    void hardDcIs20() {
        assertEquals(20, DifficultyTier.HARD.dc());
    }

    @Test
    void veryHardDcIs25() {
        assertEquals(25, DifficultyTier.VERY_HARD.dc());
    }

    @Test
    void nearlyImpossibleDcIs30() {
        assertEquals(30, DifficultyTier.NEARLY_IMPOSSIBLE.dc());
    }

    @Test
    void hasExactlySixTiers() {
        assertEquals(6, DifficultyTier.values().length);
    }
}
