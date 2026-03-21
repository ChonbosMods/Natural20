package com.chonbosmods.cave;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.*;

/**
 * Detects underground air pockets in chunks using column sampling and BFS flood-fill.
 * Runs during ChunkPreLoadProcessEvent for every loading chunk.
 */
public class CaveVoidScanner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|CaveScanner");
    private static final int MIN_Y = 10;
    private static final int MAX_Y = 90;
    private static final int SAMPLE_STEP = 4;
    private static final int MIN_VOLUME = 25_000;
    private static final int FLOOD_FILL_CAP = 50_000;
    private static final int CHUNK_SIZE = 32;

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final CaveVoidRegistry registry;

    public CaveVoidScanner(CaveVoidRegistry registry) {
        this.registry = registry;
    }

    /**
     * Scan a chunk for underground air pockets by sampling columns and flood-filling.
     *
     * @param world       the world to read blocks from
     * @param chunkBlockX the block X coordinate of the chunk's west edge
     * @param chunkBlockZ the block Z coordinate of the chunk's north edge
     */
    public void scanChunk(World world, int chunkBlockX, int chunkBlockZ) {
        long chunkKey = ((long) chunkBlockX << 32) | (chunkBlockZ & 0xFFFFFFFFL);

        for (int x = chunkBlockX; x < chunkBlockX + CHUNK_SIZE; x += SAMPLE_STEP) {
            for (int z = chunkBlockZ; z < chunkBlockZ + CHUNK_SIZE; z += SAMPLE_STEP) {
                for (int y = MAX_Y; y >= MIN_Y; y--) {
                    BlockType bt = world.getBlockType(x, y, z);
                    if (bt != null && bt.getMaterial() == BlockMaterial.Solid) continue;

                    // Skip if too close to an already-registered void
                    if (isTooCloseToExisting(x, z)) break;

                    FloodFillResult result = floodFill(world, x, y, z);
                    if (result != null && result.volume() >= MIN_VOLUME) {
                        // Reject voids too narrow to fit a structure
                        int xExtent = result.maxX() - result.minX() + 1;
                        int zExtent = result.maxZ() - result.minZ() + 1;
                        if (xExtent < 25 || zExtent < 25) {
                            break; // too narrow, skip to next column
                        }

                        CaveVoidRecord record = new CaveVoidRecord(
                                result.centerX(), result.centerY(), result.centerZ(),
                                result.minX(), result.minY(), result.minZ(),
                                result.maxX(), result.maxY(), result.maxZ(),
                                result.volume(), result.floorPositions(),
                                chunkKey
                        );
                        registry.register(record);
                        LOGGER.atInfo().log("Found cave void: center=(%d,%d,%d) volume=%d span=%dx%d",
                                result.centerX(), result.centerY(), result.centerZ(),
                                result.volume(), xExtent, zExtent);
                    }

                    // After processing this air pocket (registered or not), move to the next column
                    break;
                }
            }
        }
    }

    /**
     * Check whether a position is within 16 blocks (horizontal) of any existing void center.
     */
    private boolean isTooCloseToExisting(int x, int z) {
        for (CaveVoidRecord existing : registry.getAll()) {
            int dx = existing.getCenterX() - x;
            int dz = existing.getCenterZ() - z;
            if (dx * dx + dz * dz < 16 * 16) {
                return true;
            }
        }
        return false;
    }

    /**
     * BFS flood-fill from a starting air block, measuring contiguous air volume
     * and collecting floor positions.
     */
    private FloodFillResult floodFill(World world, int startX, int startY, int startZ) {
        Queue<int[]> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(new int[]{startX, startY, startZ});
        visited.add(packPos(startX, startY, startZ));

        int minX = startX, minY = startY, minZ = startZ;
        int maxX = startX, maxY = startY, maxZ = startZ;
        int volume = 0;
        List<int[]> floorPositions = new ArrayList<>();

        while (!queue.isEmpty() && volume < FLOOD_FILL_CAP) {
            int[] pos = queue.poll();
            int x = pos[0], y = pos[1], z = pos[2];

            BlockType bt = world.getBlockType(x, y, z);
            if (bt != null && bt.getMaterial() == BlockMaterial.Solid) continue;

            volume++;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);

            // Floor detection: if block below is solid
            BlockType below = world.getBlockType(x, y - 1, z);
            if (below != null && below.getMaterial() == BlockMaterial.Solid) {
                floorPositions.add(new int[]{x, y, z});
            }

            // 6-directional expansion
            for (int[] dir : DIRS) {
                int nx = x + dir[0], ny = y + dir[1], nz = z + dir[2];
                if (ny < MIN_Y || ny > MAX_Y) continue;
                long key = packPos(nx, ny, nz);
                if (visited.add(key)) {
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }

        if (volume == 0) return null;

        int centerX = (minX + maxX) / 2;
        int centerY = (minY + maxY) / 2;
        int centerZ = (minZ + maxZ) / 2;
        return new FloodFillResult(centerX, centerY, centerZ, minX, minY, minZ, maxX, maxY, maxZ, volume, floorPositions);
    }

    private static long packPos(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFF) << 20) | (z & 0xFFFFF);
    }

    private record FloodFillResult(int centerX, int centerY, int centerZ,
                                   int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ,
                                   int volume, List<int[]> floorPositions) {}
}
