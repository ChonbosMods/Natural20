package com.chonbosmods.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Nat20HeightmapSamplerTest {

    @Test
    void sampleResult_exposesYAndSlope() {
        Nat20HeightmapSampler.SampleResult r = new Nat20HeightmapSampler.SampleResult(64, 2, false);
        assertEquals(64, r.y());
        assertEquals(2, r.slopeDelta());
        assertFalse(r.tooSteep());
    }

    @Test
    void mode_hasThreeValues() {
        assertEquals(3, Nat20HeightmapSampler.Mode.values().length);
    }
}
