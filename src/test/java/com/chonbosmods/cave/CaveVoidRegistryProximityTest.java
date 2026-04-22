package com.chonbosmods.cave;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CaveVoidRegistryProximityTest {

    @Test
    void isNearAnyVoidRespectsBothClaimedAndUnclaimed(@TempDir Path tmp) {
        CaveVoidRegistry reg = new CaveVoidRegistry();
        reg.setSaveFile(tmp.resolve("cave_voids.json"));

        // Unclaimed void at (100, 64, 100).
        CaveVoidRecord unclaimed = new CaveVoidRecord(
                100, 64, 100,
                90, 60, 90,
                110, 68, 110,
                1000, List.of(),
                0L);
        reg.register(unclaimed);

        // Claimed void at (300, 64, 300).
        CaveVoidRecord claimed = new CaveVoidRecord(
                300, 64, 300,
                290, 60, 290,
                310, 68, 310,
                1000, List.of(),
                0L);
        claimed.claim("settlement-cell-1");
        reg.register(claimed);

        assertTrue(reg.isNearAnyVoid(110, 110, 64),
                "unclaimed void within 64 blocks returns true");
        assertTrue(reg.isNearAnyVoid(310, 310, 64),
                "claimed void within 64 blocks still returns true");
        assertFalse(reg.isNearAnyVoid(1000, 1000, 64),
                "no void within 64 blocks returns false");
    }
}
