package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkerScanTest {

    @Test
    void recordHoldsAllFields() {
        MarkerScan scan = new MarkerScan(
            new Vector3i(0, 0, 0),
            new Vector3i(1, 0, 0),
            new Vector3i(1, 0, 0),
            List.of(new Vector3i(5, 0, 5)),
            List.of(new Vector3i(10, 0, 10)),
            List.of()
        );
        assertEquals(new Vector3i(0, 0, 0), scan.anchorLocal());
        assertEquals(new Vector3i(1, 0, 0), scan.directionLocal());
        assertEquals(new Vector3i(1, 0, 0), scan.directionVector());
        assertEquals(1, scan.npcSpawnsLocal().size());
        assertEquals(1, scan.mobGroupSpawnsLocal().size());
        assertEquals(0, scan.chestSpawnsLocal().size());
    }
}
