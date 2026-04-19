package com.chonbosmods.progression.ambient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmbientSpawnConfigTest {

    @Test
    void loadsShippedDefaults() {
        AmbientSpawnConfig cfg = AmbientSpawnConfig.load();
        assertEquals(0.005, cfg.rollChance(), 1e-9);
        assertEquals(300_000L, cfg.cooldownMillis());
        assertEquals(50, cfg.minDistanceFromPlayer());
        assertEquals(100, cfg.maxDistanceFromPlayer());
        assertEquals(5, cfg.anchorRetries());
        assertEquals(64, cfg.poiExclusionBlocks());
        assertEquals(96, cfg.settlementExclusionBlocks());
        assertEquals(200, cfg.groupAnchorExclusionBlocks());
        assertEquals(1_800_000L, cfg.decayWindowMillis());
        assertEquals(150, cfg.decayPlayerNearRadius());
        assertEquals(300_000L, cfg.decaySweepIntervalMillis());
    }
}
