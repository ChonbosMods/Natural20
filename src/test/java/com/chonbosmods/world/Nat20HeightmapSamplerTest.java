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

    @Test
    void isTreeBlockName_matchesLogsLeavesFoliageBranches() {
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Drywood_Log"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Oak_Leaves"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Pine_Foliage"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Birch_Branch"));
    }

    @Test
    void isTreeBlockName_rejectsPlanksAndNonWood() {
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Wood_Drywood_Planks"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Soil_Grass"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Stone_Granite"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName(""));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName(null));
    }
}
