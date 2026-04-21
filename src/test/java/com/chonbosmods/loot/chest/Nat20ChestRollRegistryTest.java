package com.chonbosmods.loot.chest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Nat20ChestRollRegistryTest {

    @Test
    void freshRegistryReportsNothingRolled(@TempDir Path tmp) {
        Nat20ChestRollRegistry reg = new Nat20ChestRollRegistry(tmp);
        assertFalse(reg.hasBeenRolled(1, 64, 2));
    }

    @Test
    void markRolledThenQueryTrue(@TempDir Path tmp) {
        Nat20ChestRollRegistry reg = new Nat20ChestRollRegistry(tmp);
        reg.markRolled(1, 64, 2);
        assertTrue(reg.hasBeenRolled(1, 64, 2));
        assertFalse(reg.hasBeenRolled(1, 64, 3), "different position should not be marked");
    }

    @Test
    void markRolledIsIdempotent(@TempDir Path tmp) {
        Nat20ChestRollRegistry reg = new Nat20ChestRollRegistry(tmp);
        reg.markRolled(5, 60, 5);
        reg.markRolled(5, 60, 5);
        reg.markRolled(5, 60, 5);
        assertTrue(reg.hasBeenRolled(5, 60, 5));
    }

    @Test
    void saveAndLoadRoundTripPreservesState(@TempDir Path tmp) {
        Nat20ChestRollRegistry a = new Nat20ChestRollRegistry(tmp);
        a.markRolled(10, 70, 10);
        a.markRolled(-1, 5, -1);
        a.save();

        Nat20ChestRollRegistry b = new Nat20ChestRollRegistry(tmp);
        b.load();
        assertTrue(b.hasBeenRolled(10, 70, 10));
        assertTrue(b.hasBeenRolled(-1, 5, -1));
        assertFalse(b.hasBeenRolled(0, 0, 0));
    }

    @Test
    void negativeCoordinatesEncodeDistinctly(@TempDir Path tmp) {
        Nat20ChestRollRegistry reg = new Nat20ChestRollRegistry(tmp);
        reg.markRolled(-100, 64, 50);
        assertTrue(reg.hasBeenRolled(-100, 64, 50));
        assertFalse(reg.hasBeenRolled(100, 64, 50), "sign must matter");
    }
}
