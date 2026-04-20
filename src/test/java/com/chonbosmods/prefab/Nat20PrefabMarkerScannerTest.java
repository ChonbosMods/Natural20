package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Nat20PrefabMarkerScannerTest {

    // Arbitrary distinct IDs used only in-memory for these tests. They don't need
    // to match any real asset-map index because FakePrefabBuffer emits whatever
    // ID its seed cells were constructed with.
    private static final int STRUCTURAL_ID = 1;

    @BeforeAll
    static void seedMarkerIds() {
        Nat20PrefabConstants.anchorId = 100;
        Nat20PrefabConstants.directionId = 101;
        Nat20PrefabConstants.npcSpawnId = 102;
        Nat20PrefabConstants.mobGroupSpawnId = 103;
        Nat20PrefabConstants.chestSpawnId = 104;
        Nat20PrefabConstants.forceEmptyId = 105;
    }

    @Test
    void scanFindsAnchorAndDirection() {
        FakePrefabBuffer buffer = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.anchorId),
                new FakePrefabBuffer.Cell(1, 0, 0, Nat20PrefabConstants.directionId),
                // ignored structural block:
                new FakePrefabBuffer.Cell(2, 0, 0, STRUCTURAL_ID)
        ));

        MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer);

        assertEquals(new Vector3i(0, 0, 0), scan.anchorLocal());
        assertEquals(new Vector3i(1, 0, 0), scan.directionLocal());
        assertEquals(new Vector3i(1, 0, 0), scan.directionVector());
    }

    @Test
    void scanBucketsMultipleSpawns() {
        FakePrefabBuffer buffer = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.anchorId),
                new FakePrefabBuffer.Cell(1, 0, 0, Nat20PrefabConstants.directionId),
                new FakePrefabBuffer.Cell(3, 0, 3, Nat20PrefabConstants.npcSpawnId),
                new FakePrefabBuffer.Cell(4, 0, 3, Nat20PrefabConstants.npcSpawnId),
                new FakePrefabBuffer.Cell(5, 0, 3, Nat20PrefabConstants.npcSpawnId),
                new FakePrefabBuffer.Cell(6, 0, 6, Nat20PrefabConstants.mobGroupSpawnId)
        ));

        MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer);

        assertEquals(3, scan.npcSpawnsLocal().size());
        assertEquals(1, scan.mobGroupSpawnsLocal().size());
        assertEquals(0, scan.chestSpawnsLocal().size());
    }

    @Test
    void scanThrowsOnMissingAnchor() {
        FakePrefabBuffer buffer = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(1, 0, 0, Nat20PrefabConstants.directionId)
        ));

        assertThrows(IllegalArgumentException.class,
                () -> Nat20PrefabMarkerScanner.scan(buffer));
    }

    @Test
    void scanThrowsOnMultipleAnchors() {
        FakePrefabBuffer buffer = new FakePrefabBuffer(List.of(
                new FakePrefabBuffer.Cell(0, 0, 0, Nat20PrefabConstants.anchorId),
                new FakePrefabBuffer.Cell(0, 1, 0, Nat20PrefabConstants.anchorId),
                new FakePrefabBuffer.Cell(1, 0, 0, Nat20PrefabConstants.directionId)
        ));

        assertThrows(IllegalArgumentException.class,
                () -> Nat20PrefabMarkerScanner.scan(buffer));
    }
}
