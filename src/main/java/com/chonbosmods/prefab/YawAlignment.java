package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;

/**
 * Compute the Hytale {@link Rotation} that takes a prefab-local cardinal facing
 * onto a desired world cardinal facing. Both inputs must be horizontal unit
 * vectors (one of +X, -X, +Z, -Z); vertical components or diagonals throw.
 *
 * <p>The rotation convention matches Hytale's {@code PrefabRotation} and the
 * pre-refactor {@code UndergroundStructurePlacer} wall-direction table:
 * {@code Rotation.TwoSeventy} rotates prefab-local +Z onto world +X.
 */
public final class YawAlignment {
    private YawAlignment() {}

    /**
     * Rotation that takes {@code prefabDir} onto {@code worldDir}.
     * @throws IllegalArgumentException if either argument is not a horizontal cardinal unit vector.
     */
    public static Rotation computeYawToAlign(Vector3i prefabDir, Vector3i worldDir) {
        int pa = axisIndex(prefabDir);
        int wa = axisIndex(worldDir);
        int steps = Math.floorMod(wa - pa, 4);
        return switch (steps) {
            case 0 -> Rotation.None;
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> throw new AssertionError("floorMod returned unexpected value: " + steps);
        };
    }

    /** Cardinal indices: 0=+X, 1=+Z, 2=-X, 3=-Z. Counterclockwise ordering. */
    private static int axisIndex(Vector3i v) {
        if (v.getY() != 0) {
            throw new IllegalArgumentException("Direction must be horizontal; got " + v);
        }
        if (v.getX() ==  1 && v.getZ() ==  0) return 0;
        if (v.getX() ==  0 && v.getZ() ==  1) return 1;
        if (v.getX() == -1 && v.getZ() ==  0) return 2;
        if (v.getX() ==  0 && v.getZ() == -1) return 3;
        throw new IllegalArgumentException("Not a cardinal horizontal unit vector: " + v);
    }
}
