package com.chonbosmods.dungeon;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Shared utilities for grid prefab save/preview commands.
 * Provides raycast, cardinal direction snapping, and coordinate transforms
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
     * Compute a direction unit vector from yaw and pitch (radians).
     *
     * Hytale convention:
     *   dx = -sin(yaw) * cos(pitch)
     *   dy = -sin(pitch)
     *   dz = -cos(yaw) * cos(pitch)
     */
    public static Vector3d getDirection(float yaw, float pitch) {
        double cosPitch = Math.cos(pitch);
        double dx = -Math.sin(yaw) * cosPitch;
        double dy = -Math.sin(pitch);
        double dz = -Math.cos(yaw) * cosPitch;
        return new Vector3d(dx, dy, dz);
    }

    /**
     * Raycast from eye position along direction to find the first solid block.
     * Returns null if no block is found within maxDistance.
     */
    public static Vector3i getTargetBlock(World world, Vector3d eyePos, Vector3d direction, double maxDistance) {
        double step = 0.25;
        for (double d = 0.5; d <= maxDistance; d += step) {
            int bx = (int) Math.floor(eyePos.getX() + direction.getX() * d);
            int by = (int) Math.floor(eyePos.getY() + direction.getY() * d);
            int bz = (int) Math.floor(eyePos.getZ() + direction.getZ() * d);
            BlockType bt = world.getBlockType(bx, by, bz);
            if (bt != null && !"Empty".equals(bt.getId())) {
                return new Vector3i(bx, by, bz);
            }
        }
        return null;
    }

    /**
     * Compute the world-space origin (minimum X, minimum Z corner) of the scan region.
     * The region starts 1 block above the target block and extends in the direction
     * the player is facing (forward) and to their right.
     *
     * <p>For SOUTH/NORTH facing, the world-space region is blockW wide (X) and blockD deep (Z).
     * For EAST/WEST facing, the world-space region is blockD wide (X) and blockW deep (Z)
     * because width maps to the right axis and depth maps to the forward axis.
     *
     * @param target the block the player is looking at
     * @param facing the player's cardinal facing direction
     * @param blockW canonical width (right-axis extent in blocks)
     * @param blockD canonical depth (forward-axis extent in blocks)
     * @return the minimum-corner origin of the world-space region
     */
    public static Vector3i computeRegionOrigin(Vector3i target, Face facing, int blockW, int blockD) {
        int tx = target.getX();
        int ty = target.getY() + 1;
        int tz = target.getZ();
        return switch (facing) {
            // Forward = +Z, Right = +X: origin is at target, extends +X and +Z
            case SOUTH -> new Vector3i(tx, ty, tz);
            // Forward = -Z, Right = -X: far corner is at target, origin is min corner
            case NORTH -> new Vector3i(tx - blockW + 1, ty, tz - blockD + 1);
            // Forward = +X, Right = -Z: world X extent = blockD, world Z extent = blockW
            case EAST -> new Vector3i(tx, ty, tz - blockW + 1);
            // Forward = -X, Right = +Z: world X extent = blockD, world Z extent = blockW
            case WEST -> new Vector3i(tx - blockD + 1, ty, tz);
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
