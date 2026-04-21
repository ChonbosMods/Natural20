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
    void isTreeBlockName_matchesPlantLeaves() {
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Plant_Leaves_Oak"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Plant_Leaves_Snow"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Plant_Leaves_Autumn_Floor"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Plant_Leaves_Fig_Blue"));
    }

    @Test
    void isTreeBlockName_matchesTrunksBranchesRoots() {
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Oak_Trunk"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Dry_Trunk_Full"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Oak_Trunk_Half"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Birch_Branch_Corner"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Bamboo_Branch_Long"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Amber_Branch_Short"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Oak_Roots"));
        assertTrue(Nat20HeightmapSampler.isTreeBlockName("Wood_Palm_Roots"));
    }

    @Test
    void isTreeBlockName_rejectsCraftedAndNonWood() {
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Wood_Oak_Planks"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Wood_Oak_Planks_Half"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Wood_Sticks"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Wood_Stripped_Deco"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Prototype_Wood_Beech_Branch_X"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Soil_Grass"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Stone_Granite"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName("Plant_Flower_Rose"));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName(""));
        assertFalse(Nat20HeightmapSampler.isTreeBlockName(null));
    }
}
