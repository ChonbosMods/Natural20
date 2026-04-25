package com.chonbosmods.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Nat20HeightmapSamplerTest {

    @Test
    void sampleResult_exposesYSlopeAndWet() {
        Nat20HeightmapSampler.SampleResult r =
            new Nat20HeightmapSampler.SampleResult(64, 2, false, 0, false);
        assertEquals(64, r.y());
        assertEquals(2, r.slopeDelta());
        assertFalse(r.tooSteep());
        assertEquals(0, r.maxSubmergedDepth());
        assertFalse(r.tooWet());
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

    @Test
    void walkDown_returnsYOnePastSolid_whenStartIsLeafCanopy() {
        // Simulated column: y=68-70 leaves, y=67 trunk, y<=66 dirt (solid).
        // Start at y=70, expect y=67 (66 is dirt top + 1).
        java.util.function.IntFunction<String> names = y -> {
            if (y >= 68) return "Plant_Leaves_Oak";
            if (y == 67) return "Wood_Oak_Trunk";
            return "Soil_Dirt";
        };
        java.util.function.IntPredicate isSolid = y -> y <= 66; // dirt is solid-opacity
        java.util.function.IntPredicate noFluid = y -> false;
        int result = Nat20HeightmapSampler.walkDownToSolidGround(70, 30, names, isSolid, noFluid);
        assertEquals(67, result, "should land on first non-tree solid block top + 1");
    }

    @Test
    void walkDown_returnsStartPlusOne_whenStartIsAlreadyOnGround() {
        java.util.function.IntFunction<String> names = y -> "Stone_Granite";
        java.util.function.IntPredicate isSolid = y -> true;
        java.util.function.IntPredicate noFluid = y -> false;
        int result = Nat20HeightmapSampler.walkDownToSolidGround(64, 30, names, isSolid, noFluid);
        assertEquals(65, result);
    }

    @Test
    void walkDown_bailsAtMaxStepsReturningSentinel() {
        // All transparent: should bail out after maxSteps and return 0 (sentinel).
        java.util.function.IntFunction<String> names = y -> "Air";
        java.util.function.IntPredicate isSolid = y -> false;
        java.util.function.IntPredicate noFluid = y -> false;
        int result = Nat20HeightmapSampler.walkDownToSolidGround(200, 20, names, isSolid, noFluid);
        assertEquals(0, result);
    }

    @Test
    void walkDown_rejectsColumnContainingFluidByName() {
        // Defensive path: blockId-as-fluid name. Some servers may stamp fluids as blocks.
        java.util.function.IntFunction<String> names = y -> {
            if (y >= 66) return "Air";
            if (y >= 60) return "Fluid_Water";
            return "Soil_Dirt";
        };
        java.util.function.IntPredicate isSolid = y -> y <= 59;
        java.util.function.IntPredicate noFluid = y -> false;
        int result = Nat20HeightmapSampler.walkDownToSolidGround(70, 30, names, isSolid, noFluid);
        assertEquals(0, result, "fluid blockId name must reject the probe");
    }

    @Test
    void walkDown_rejectsColumnWhenFluidLayerPresent() {
        // Primary path: Hytale's real water is air-blocks with fluidId set. Name check
        // misses this; the isFluidAt predicate must catch it.
        java.util.function.IntFunction<String> names = y -> y <= 59 ? "Soil_Dirt" : "Air";
        java.util.function.IntPredicate isSolid = y -> y <= 59;
        java.util.function.IntPredicate isFluid = y -> y >= 60 && y <= 65; // water between 60-65
        int result = Nat20HeightmapSampler.walkDownToSolidGround(70, 30, names, isSolid, isFluid);
        assertEquals(0, result, "fluid layer must reject the probe via isFluidAt");
    }

    @Test
    void reduce_minPicksLowest() {
        int[] heights = {64, 65, 68, 63, 66};
        assertEquals(63, Nat20HeightmapSampler.reduce(heights, Nat20HeightmapSampler.Mode.MIN));
    }

    @Test
    void reduce_medianPicksMiddle() {
        int[] heights = {64, 65, 68, 63, 66};
        // sorted: 63, 64, 65, 66, 68 -> median = 65
        assertEquals(65, Nat20HeightmapSampler.reduce(heights, Nat20HeightmapSampler.Mode.MEDIAN));
    }

    @Test
    void reduce_entryAnchorPicksFirstElement() {
        int[] heights = {99, 65, 68, 63, 66};
        assertEquals(99, Nat20HeightmapSampler.reduce(heights, Nat20HeightmapSampler.Mode.ENTRY_ANCHOR));
    }

    @Test
    void slopeDelta_isMaxMinusMin() {
        assertEquals(5, Nat20HeightmapSampler.slopeDelta(new int[]{64, 65, 68, 63, 66}));
        assertEquals(0, Nat20HeightmapSampler.slopeDelta(new int[]{70, 70, 70, 70, 70}));
    }
}
