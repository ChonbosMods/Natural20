package com.chonbosmods.dungeon;

import com.hypixel.hytale.math.vector.Vector3i;

/**
 * Shared utilities for grid prefab save/preview commands.
 * Provides cardinal direction snapping and coordinate transforms
 * between world-space and canonical (south-facing) local-space.
 */
public final class GridPrefabUtil {

    private static final double TWO_PI = 2 * Math.PI;

    private GridPrefabUtil() {}

    /**
     * Snap a yaw angle (radians) to the nearest cardinal Face.
     *
     * Hytale yaw convention (from SettlementNpcRotationTicker):
     *   SOUTH (+Z) = PI,  NORTH (-Z) = 0/2PI,
     *   WEST  (-X) = PI/2,  EAST  (+X) = 3PI/2
     */
    public static Face getCardinalFacing(float yaw) {
        double normalized = ((yaw % TWO_PI) + TWO_PI) % TWO_PI;
        // PI = south, PI/2 = west, 0/2PI = north, 3PI/2 = east
        // Quadrant boundaries at PI/4 intervals centered on each cardinal
        if (normalized >= 3 * Math.PI / 4 && normalized < 5 * Math.PI / 4) return Face.SOUTH;
        if (normalized >= Math.PI / 4 && normalized < 3 * Math.PI / 4) return Face.WEST;
        if (normalized >= 5 * Math.PI / 4 && normalized < 7 * Math.PI / 4) return Face.EAST;
        return Face.NORTH;
    }

    /**
     * Compute the world-space origin (minimum X, minimum Z corner) of the prefab region.
     *
     * <p>The player stands at the back-left corner of the prefab. The region extends
     * FORWARD (facing direction) and to the RIGHT from the player's position.
     * Floor is at the player's feet (playerPos Y).
     *
     * <p>The origin is always the min-X, min-Z corner of the world-space bounding box.
     *
     * @param playerPos the block position at the player's feet
     * @param facing the player's cardinal facing direction
     * @param blockW canonical width (right-axis extent in blocks)
     * @param blockD canonical depth (forward-axis extent in blocks)
     * @return the minimum-corner origin of the world-space region
     */
    public static Vector3i computeRegionOrigin(Vector3i playerPos, Face facing, int blockW, int blockD) {
        int px = playerPos.getX();
        int py = playerPos.getY();
        int pz = playerPos.getZ();
        return switch (facing) {
            // Forward = +Z, Right = +X: player at min corner
            case SOUTH -> new Vector3i(px, py, pz);
            // Forward = -Z, Right = -X: player at max corner
            case NORTH -> new Vector3i(px - blockW + 1, py, pz - blockD + 1);
            // Forward = +X, Right = -Z: player at min-X, max-Z corner
            case EAST -> new Vector3i(px, py, pz - blockW + 1);
            // Forward = -X, Right = +Z: player at max-X, min-Z corner
            case WEST -> new Vector3i(px - blockD + 1, py, pz);
        };
    }

    /**
     * Get the world-space X and Z extents for a region based on facing.
     * For N/S facing: worldW=blockW, worldD=blockD (canonical).
     * For E/W facing: worldW=blockD, worldD=blockW (swapped).
     *
     * @return {worldExtentX, worldExtentZ}
     */
    public static int[] getWorldExtents(Face facing, int blockW, int blockD) {
        return switch (facing) {
            case SOUTH, NORTH -> new int[]{blockW, blockD};
            case EAST, WEST -> new int[]{blockD, blockW};
        };
    }

    /**
     * Transform a world-space offset (relative to region origin) into canonical
     * local-space coordinates (south-facing orientation: X=right, Z=forward).
     *
     * @param wx world offset X (0 to worldExtentX - 1)
     * @param wz world offset Z (0 to worldExtentZ - 1)
     * @param facing the player's cardinal facing direction
     * @param worldExtX world-space X extent of the region
     * @param worldExtZ world-space Z extent of the region
     * @return {localX, localZ} in canonical south-facing space
     */
    public static int[] worldToLocal(int wx, int wz, Face facing, int worldExtX, int worldExtZ) {
        return switch (facing) {
            case SOUTH -> new int[]{wx, wz};
            case NORTH -> new int[]{worldExtX - 1 - wx, worldExtZ - 1 - wz};
            case EAST  -> new int[]{worldExtZ - 1 - wz, wx};
            case WEST  -> new int[]{wz, worldExtX - 1 - wx};
        };
    }

    /**
     * Transform canonical local-space coordinates (south-facing: X=right, Z=forward)
     * into a world-space offset relative to the region origin.
     *
     * @param lx local X (0 to blockW - 1)
     * @param lz local Z (0 to blockD - 1)
     * @param facing the player's cardinal facing direction
     * @param blockW canonical width (right-axis extent)
     * @param blockD canonical depth (forward-axis extent)
     * @return {worldOffsetX, worldOffsetZ} relative to region origin
     */
    public static int[] localToWorld(int lx, int lz, Face facing, int blockW, int blockD) {
        return switch (facing) {
            case SOUTH -> new int[]{lx, lz};
            case NORTH -> new int[]{blockW - 1 - lx, blockD - 1 - lz};
            case EAST  -> new int[]{lz, blockW - 1 - lx};
            case WEST  -> new int[]{blockD - 1 - lz, lx};
        };
    }
}
