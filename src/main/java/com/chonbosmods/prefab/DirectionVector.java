package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;

/**
 * Utilities for the anchor+direction marker pair. A prefab's horizontal facing
 * is encoded as the offset from its {@code Nat20_Anchor} block to its
 * {@code Nat20_Direction} block; this class snaps that offset to a cardinal
 * unit vector.
 */
public final class DirectionVector {
    private DirectionVector() {}

    /**
     * Snap an arbitrary offset to the nearest cardinal horizontal unit vector.
     * The Y component is ignored : direction is a horizontal facing. Ties between
     * X and Z break in favor of X so the result is deterministic.
     *
     * @throws IllegalArgumentException if both X and Z are zero.
     */
    public static Vector3i snapToCardinal(int dx, int dy, int dz) {
        int absX = Math.abs(dx);
        int absZ = Math.abs(dz);
        if (absX == 0 && absZ == 0) {
            throw new IllegalArgumentException(
                "Direction offset is zero on both horizontal axes; the direction block must not coincide horizontally with the anchor");
        }
        if (absX >= absZ) {
            return new Vector3i(Integer.signum(dx), 0, 0);
        }
        return new Vector3i(0, 0, Integer.signum(dz));
    }
}
