package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DirectionVectorTest {

    @Test
    void cardinalUnitVectorsPassThrough() {
        assertEquals(new Vector3i(1, 0, 0), DirectionVector.snapToCardinal(1, 0, 0));
        assertEquals(new Vector3i(-1, 0, 0), DirectionVector.snapToCardinal(-1, 0, 0));
        assertEquals(new Vector3i(0, 0, 1), DirectionVector.snapToCardinal(0, 0, 1));
        assertEquals(new Vector3i(0, 0, -1), DirectionVector.snapToCardinal(0, 0, -1));
    }

    @Test
    void longerOffsetsSnapToDominantAxis() {
        // (3, 0, 1) -> dominant X -> (1, 0, 0)
        assertEquals(new Vector3i(1, 0, 0), DirectionVector.snapToCardinal(3, 0, 1));
        // (-1, 0, -2) -> dominant -Z -> (0, 0, -1)
        assertEquals(new Vector3i(0, 0, -1), DirectionVector.snapToCardinal(-1, 0, -2));
    }

    @Test
    void zeroHorizontalOffsetThrows() {
        // No horizontal offset at all: undefined facing.
        assertThrows(IllegalArgumentException.class, () -> DirectionVector.snapToCardinal(0, 0, 0));
        // Y-only offset is still zero horizontally.
        assertThrows(IllegalArgumentException.class, () -> DirectionVector.snapToCardinal(0, 5, 0));
    }

    @Test
    void yAxisIgnoredForCardinalSnap() {
        // (0, 5, 1) -> dominant Z -> (0, 0, 1). Y is irrelevant.
        assertEquals(new Vector3i(0, 0, 1), DirectionVector.snapToCardinal(0, 5, 1));
    }

    @Test
    void tieBreakPrefersX() {
        // Equal |x| and |z|: prefer X so the snap is deterministic.
        assertEquals(new Vector3i(1, 0, 0), DirectionVector.snapToCardinal(2, 0, 2));
    }
}
