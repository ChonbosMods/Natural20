package com.chonbosmods.cave;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Places a prefab structure adjacent to a cave void and carves a raw connecting tunnel.
 */
public class UndergroundStructurePlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|CavePlacer");
    private static final String TEST_PREFAB_KEY = "Nat20/dungeon/testDungeon";
    private static final int TUNNEL_WIDTH = 3;
    private static final int TUNNEL_HEIGHT = 4;
    private static final int STRUCTURE_OFFSET = 15; // half-width of testDungeon (10) + gap (5)
    private static final int DEFER_TICKS = 5;

    /**
     * Place a structure adjacent to the given cave void and carve a connecting tunnel.
     *
     * @param world     the world to place into
     * @param voidRecord the cave void to place adjacent to
     * @param store     component accessor for entity persistence
     * @return a future that completes with the entrance position (tunnel start), or null on failure
     */
    public CompletableFuture<Vector3i> placeAtVoid(World world, CaveVoidRecord voidRecord,
                                                    ComponentAccessor<EntityStore> store) {
        CompletableFuture<Vector3i> result = new CompletableFuture<>();

        // 1. Pick the closest floor position to the void's center
        List<int[]> floorPositions = voidRecord.getFloorPositions();
        if (floorPositions == null || floorPositions.isEmpty()) {
            LOGGER.atWarning().log("No floor positions in void at (%d, %d, %d)",
                    voidRecord.getCenterX(), voidRecord.getCenterY(), voidRecord.getCenterZ());
            result.complete(null);
            return result;
        }

        int cx = voidRecord.getCenterX();
        int cy = voidRecord.getCenterY();
        int cz = voidRecord.getCenterZ();

        int[] bestFloor = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] fp : floorPositions) {
            double dx = fp[0] - cx;
            double dy = fp[1] - cy;
            double dz = fp[2] - cz;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                bestFloor = fp;
            }
        }

        int floorX = bestFloor[0];
        int floorY = bestFloor[1];
        int floorZ = bestFloor[2];

        // 2. Determine cardinal direction: pick axis with largest void extent, offset perpendicular
        int extentX = voidRecord.getMaxX() - voidRecord.getMinX();
        int extentZ = voidRecord.getMaxZ() - voidRecord.getMinZ();

        // Offset perpendicular to the largest extent axis
        final int offsetX;
        final int offsetZ;
        if (extentX >= extentZ) {
            // Void is wider in X: offset in Z direction
            offsetX = 0;
            offsetZ = (floorZ >= cz) ? STRUCTURE_OFFSET : -STRUCTURE_OFFSET;
        } else {
            // Void is wider in Z: offset in X direction
            offsetX = (floorX >= cx) ? STRUCTURE_OFFSET : -STRUCTURE_OFFSET;
            offsetZ = 0;
        }

        // 3. Calculate structure placement position
        int structX = floorX + offsetX;
        int structY = floorY;
        int structZ = floorZ + offsetZ;
        Vector3i structPos = new Vector3i(structX, structY, structZ);

        LOGGER.atInfo().log("Placing structure at (%d, %d, %d) for void at (%d, %d, %d)",
                structX, structY, structZ, cx, cy, cz);

        // 4. Load the prefab buffer
        Path prefabPath = findPrefabPath();
        if (prefabPath == null) {
            LOGGER.atSevere().log("Prefab not found: %s", TEST_PREFAB_KEY);
            result.complete(null);
            return result;
        }

        IPrefabBuffer buffer;
        try {
            buffer = PrefabBufferUtil.getCached(prefabPath);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load prefab buffer: %s", prefabPath);
            result.complete(null);
            return result;
        }

        LOGGER.atInfo().log("Loaded prefab buffer: %s (size: %dx%dx%d)",
                prefabPath,
                buffer.getMaxX() - buffer.getMinX(),
                buffer.getMaxY() - buffer.getMinY(),
                buffer.getMaxZ() - buffer.getMinZ());

        // 5. Pre-load non-ticking chunks covering the structure bounding box AND the tunnel path
        int minBlockX = Math.min(structX + buffer.getMinX(), floorX - 1);
        int maxBlockX = Math.max(structX + buffer.getMaxX(), floorX + 1);
        int minBlockZ = Math.min(structZ + buffer.getMinZ(), floorZ - 1);
        int maxBlockZ = Math.max(structZ + buffer.getMaxZ(), floorZ + 1);

        preloadChunks(world, minBlockX, minBlockZ, maxBlockX, maxBlockZ)
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.atSevere().withCause(error).log("Chunk pre-loading failed for structure placement");
                        result.complete(null);
                        return;
                    }

                    // 6. Defer ticks then paste
                    deferTicks(world, DEFER_TICKS, () -> {
                        try {
                            // 7. Paste the prefab
                            Random random = new Random();
                            PrefabUtil.paste(buffer, world, structPos, Rotation.None, true, random, 0, store);
                            LOGGER.atInfo().log("Pasted prefab at (%d, %d, %d)", structX, structY, structZ);

                            // 8. Carve the connecting tunnel from structure entrance face to void floor
                            // Tunnel starts at the structure edge facing the void
                            int tunnelStartX = structX - offsetX;
                            int tunnelStartZ = structZ - offsetZ;
                            carveTunnel(world, tunnelStartX, floorY, tunnelStartZ, floorX, floorY, floorZ);

                            Vector3i entrance = new Vector3i(tunnelStartX, floorY, tunnelStartZ);
                            LOGGER.atInfo().log("Structure placement complete. Entrance at (%d, %d, %d)",
                                    tunnelStartX, floorY, tunnelStartZ);
                            result.complete(entrance);
                        } catch (Exception e) {
                            LOGGER.atSevere().withCause(e).log("Failed to place structure or carve tunnel");
                            result.complete(null);
                        }
                    });
                });

        return result;
    }

    /**
     * Find the prefab file path. Tries asset pack lookup first, then falls back to walking
     * up the filesystem from the plugin file to find assets/Server/Prefabs/.
     */
    private Path findPrefabPath() {
        // Try asset pack lookup first
        Path assetPath = PrefabStore.get().findAssetPrefabPath(TEST_PREFAB_KEY);
        if (assetPath != null) {
            return assetPath;
        }

        // Fall back: resolve from plugin file path (dev mode)
        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile != null) {
            Path candidate = pluginFile;
            for (int i = 0; i < 4; i++) {
                Path assetsDir = candidate.resolve("assets").resolve("Server").resolve("Prefabs")
                        .resolve(TEST_PREFAB_KEY + ".prefab.json");
                if (Files.exists(assetsDir)) {
                    LOGGER.atInfo().log("Found prefab via fallback path: %s", assetsDir);
                    return assetsDir;
                }
                // Also check Server/Prefabs directly (in case plugin root IS the assets dir)
                Path directDir = candidate.resolve("Server").resolve("Prefabs")
                        .resolve(TEST_PREFAB_KEY + ".prefab.json");
                if (Files.exists(directDir)) {
                    LOGGER.atInfo().log("Found prefab via direct path: %s", directDir);
                    return directDir;
                }
                candidate = candidate.getParent();
                if (candidate == null) break;
            }
        }

        return null;
    }

    /**
     * Carve a 3-wide x 4-tall corridor of empty blocks from the structure entrance
     * to the void floor position.
     */
    private void carveTunnel(World world, int startX, int startY, int startZ,
                              int endX, int endY, int endZ) {
        int dx = endX - startX;
        int dz = endZ - startZ;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) return;

        float stepX = (float) dx / steps;
        float stepZ = (float) dz / steps;
        float stepY = (float) (endY - startY) / steps;

        boolean primaryX = Math.abs(dx) >= Math.abs(dz);

        for (int i = 0; i <= steps; i++) {
            int bx = startX + Math.round(stepX * i);
            int by = startY + Math.round(stepY * i);
            int bz = startZ + Math.round(stepZ * i);

            for (int w = -1; w <= 1; w++) {
                for (int h = 0; h < TUNNEL_HEIGHT; h++) {
                    int wx = primaryX ? bx : bx + w;
                    int wy = by + h;
                    int wz = primaryX ? bz + w : bz;
                    setBlockEmpty(world, wx, wy, wz);
                }
            }
        }

        LOGGER.atInfo().log("Carved tunnel from (%d, %d, %d) to (%d, %d, %d): %d steps",
                startX, startY, startZ, endX, endY, endZ, steps);
    }

    /**
     * Set a single block to empty/air.
     * <p>
     * TODO: Verify correct API for setting blocks to empty. Candidates to try at runtime:
     * - world.setBlockType(x, y, z, null)
     * - world.setBlock(x, y, z, null)
     * - world.removeBlock(x, y, z)
     * - BlockType.getAssetMap().getAsset("Empty") then world.setBlockType(x, y, z, empty)
     * - Chunk-level API via world.getChunk() then chunk.setBlockType()
     */
    private boolean setBlockEmptyWarned = false;

    private void setBlockEmpty(World world, int x, int y, int z) {
        // Stub: block-setting API not yet discovered
        if (!setBlockEmptyWarned) {
            LOGGER.atWarning().log("setBlockEmpty not yet implemented: tunnel carving will be skipped");
            setBlockEmptyWarned = true;
        }
    }

    /**
     * Pre-load non-ticking chunks covering the given block coordinate range.
     */
    private CompletableFuture<Void> preloadChunks(World world, int minX, int minZ, int maxX, int maxZ) {
        int minCX = ChunkUtil.chunkCoordinate(minX);
        int minCZ = ChunkUtil.chunkCoordinate(minZ);
        int maxCX = ChunkUtil.chunkCoordinate(maxX);
        int maxCZ = ChunkUtil.chunkCoordinate(maxZ);
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                futures.add(world.getNonTickingChunkAsync(ChunkUtil.indexChunk(cx, cz)));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Defer execution by the given number of world ticks.
     */
    private void deferTicks(World world, int ticks, Runnable action) {
        if (ticks <= 0) {
            world.execute(action);
        } else {
            world.execute(() -> deferTicks(world, ticks - 1, action));
        }
    }
}
