package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacedMarkersTest {

    @Test
    void recordHoldsAllFields() {
        PlacedMarkers p = new PlacedMarkers(
            new Vector3i(10, 64, 10),
            new Vector3i(1, 0, 0),
            new Vector3i(2, 3, 4),
            List.of(new Vector3d(5.5, 64, 5.5)),
            List.of(new Vector3d(15.5, 64, 15.5)),
            List.of()
        );
        assertEquals(new Vector3i(10, 64, 10), p.anchorWorld());
        assertEquals(new Vector3i(1, 0, 0), p.directionVectorWorld());
        assertEquals(new Vector3i(2, 3, 4), p.translation());
        assertEquals(1, p.npcSpawnsWorld().size());
        assertEquals(1, p.mobGroupSpawnsWorld().size());
        assertEquals(0, p.chestSpawnsWorld().size());
    }
}
