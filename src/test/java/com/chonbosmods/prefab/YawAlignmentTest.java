package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ground truth comes from the original {@code UndergroundStructurePlacer} wall-dir
 * → rotation table (pre-refactor). In that system, Dungeon2Test's entrance face is
 * prefab-local +Z. Rotation should turn the prefab's +Z facing onto the "away from
 * the nearest wall" world vector:
 * <ul>
 *   <li>wall at -X → world facing +X → Rotation.TwoSeventy (+Z → +X)</li>
 *   <li>wall at +X → world facing -X → Rotation.Ninety    (+Z → -X)</li>
 *   <li>wall at +Z → world facing -Z → Rotation.OneEighty (+Z → -Z)</li>
 *   <li>wall at -Z → world facing +Z → Rotation.None      (+Z → +Z)</li>
 * </ul>
 */
class YawAlignmentTest {

    private static final Vector3i POS_X = new Vector3i(1, 0, 0);
    private static final Vector3i NEG_X = new Vector3i(-1, 0, 0);
    private static final Vector3i POS_Z = new Vector3i(0, 0, 1);
    private static final Vector3i NEG_Z = new Vector3i(0, 0, -1);

    @Test
    void identityRotation() {
        assertEquals(Rotation.None, YawAlignment.computeYawToAlign(POS_Z, POS_Z));
        assertEquals(Rotation.None, YawAlignment.computeYawToAlign(POS_X, POS_X));
    }

    @Test
    void posZToPosXIsTwoSeventy() {
        assertEquals(Rotation.TwoSeventy, YawAlignment.computeYawToAlign(POS_Z, POS_X));
    }

    @Test
    void posZToNegXIsNinety() {
        assertEquals(Rotation.Ninety, YawAlignment.computeYawToAlign(POS_Z, NEG_X));
    }

    @Test
    void posZToNegZIsOneEighty() {
        assertEquals(Rotation.OneEighty, YawAlignment.computeYawToAlign(POS_Z, NEG_Z));
    }

    @Test
    void negXPrefabFacingToPosZIsTwoSeventy() {
        // TwoSeventy is consistent across the table: it's the rotation that maps
        // the "counterclockwise-previous" cardinal onto each starting one.
        // -X → +Z mirrors +Z → +X.
        assertEquals(Rotation.TwoSeventy, YawAlignment.computeYawToAlign(NEG_X, POS_Z));
    }

    @Test
    void nonCardinalInputThrows() {
        Vector3i diagonal = new Vector3i(1, 0, 1);
        assertThrows(IllegalArgumentException.class,
                () -> YawAlignment.computeYawToAlign(diagonal, POS_Z));
        Vector3i vertical = new Vector3i(0, 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> YawAlignment.computeYawToAlign(POS_Z, vertical));
    }
}
