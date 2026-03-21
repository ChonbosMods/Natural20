package com.chonbosmods.cave;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;

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
    private static final int DEFER_TICKS = 5;
    private static final int CARVE_MARGIN = 3;

    /**
     * Place a structure adjacent to the given cave void and carve a connecting tunnel.
     *
     * @param world     the world to place into
     * @param voidRecord the cave void to place adjacent to
     * @param store     component accessor for entity persistence
     * @return a future that completes with the entrance position (tunnel start), or null on failure
     */
    public CompletableFuture<Vector3i> placeAtVoid(World world, CaveVoidRecord voidRecord,
                                                    Store<EntityStore> store) {
        CompletableFuture<Vector3i> result = new CompletableFuture<>();

        // 1. Pick the floor position with the most vertical clearance
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
        int bestClearance = 0;
        for (int[] fp : floorPositions) {
            int clearance = 0;
            for (int y = fp[1]; y < fp[1] + 50; y++) {
                BlockType bt = world.getBlockType(fp[0], y, fp[2]);
                if (bt != null && bt.getMaterial() == BlockMaterial.Solid) break;
                clearance++;
            }
            if (clearance > bestClearance) {
                bestClearance = clearance;
                bestFloor = fp;
            }
        }
        if (bestFloor == null) {
            LOGGER.atWarning().log("No floor position with vertical clearance in void at (%d, %d, %d)",
                    cx, cy, cz);
            result.complete(null);
            return result;
        }

        int floorX = bestFloor[0];
        int floorY = bestFloor[1];
        int floorZ = bestFloor[2];

        // 2. Place directly at the floor position (inside the cave void where there's clearance)
        int structX = floorX;
        int structY = floorY;
        int structZ = floorZ;

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

        LOGGER.atInfo().log("Loaded prefab buffer: %s (size: %dx%dx%d, anchor: %d,%d,%d, columns: %d)",
                prefabPath,
                buffer.getMaxX() - buffer.getMinX(),
                buffer.getMaxY() - buffer.getMinY(),
                buffer.getMaxZ() - buffer.getMinZ(),
                buffer.getAnchorX(), buffer.getAnchorY(), buffer.getAnchorZ(),
                buffer.getColumnCount());

        // Adjust for non-zero anchors
        int pasteX = structX - buffer.getAnchorX();
        int pasteY = structY - buffer.getAnchorY();
        int pasteZ = structZ - buffer.getAnchorZ();
        Vector3i pastePos = new Vector3i(pasteX, pasteY, pasteZ);

        LOGGER.atInfo().log("Paste position: (%d, %d, %d) (anchor offset: %d, %d, %d)",
                pasteX, pasteY, pasteZ,
                buffer.getAnchorX(), buffer.getAnchorY(), buffer.getAnchorZ());

        // 5. Pre-load chunks covering the prefab footprint + carve margin
        int minChunkX = ChunkUtil.chunkCoordinate(pasteX + buffer.getMinX() - CARVE_MARGIN);
        int minChunkZ = ChunkUtil.chunkCoordinate(pasteZ + buffer.getMinZ() - CARVE_MARGIN);
        int maxChunkX = ChunkUtil.chunkCoordinate(pasteX + buffer.getMaxX() + CARVE_MARGIN);
        int maxChunkZ = ChunkUtil.chunkCoordinate(pasteZ + buffer.getMaxZ() + CARVE_MARGIN);

        List<CompletableFuture<?>> chunkFutures = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long key = ChunkUtil.indexChunk(chunkX, chunkZ);
                chunkFutures.add(world.getNonTickingChunkAsync(key));
            }
        }

        LOGGER.atInfo().log("Pre-loading %d chunks for prefab placement", chunkFutures.size());

        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.atSevere().withCause(error).log("Chunk loading timed out for structure placement");
                        result.complete(null);
                        return;
                    }
                    deferTicks(world, DEFER_TICKS, () -> {
                        try {
                            carveClearing(world, pasteX, pasteY, pasteZ, buffer);

                            Random random = new Random();
                            PrefabUtil.paste(buffer, world, pastePos, Rotation.None, true, random, 0, store);
                            LOGGER.atInfo().log("Pasted prefab at (%d, %d, %d)", pasteX, pasteY, pasteZ);

                            result.complete(pastePos);
                        } catch (Exception e) {
                            LOGGER.atSevere().withCause(e).log("Failed to paste prefab");
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
     * Carve a clearing around the prefab's bounding box so the structure is not buried.
     * Clears the prefab footprint + margin to empty, then the prefab paste overwrites
     * the interior with the structure's own blocks.
     */
    private void carveClearing(World world, int pasteX, int pasteY, int pasteZ, IPrefabBuffer buffer) {
        int fromX = pasteX + buffer.getMinX() - CARVE_MARGIN;
        int fromZ = pasteZ + buffer.getMinZ() - CARVE_MARGIN;
        int toX = pasteX + buffer.getMaxX() + CARVE_MARGIN;
        int toZ = pasteZ + buffer.getMaxZ() + CARVE_MARGIN;
        int fromY = pasteY + buffer.getMinY();
        int toY = pasteY + buffer.getMaxY() + 1;

        BlockType empty = BlockType.getAssetMap().getAsset("Empty");
        if (empty == null) {
            LOGGER.atSevere().log("Could not find Empty block type: carving skipped");
            return;
        }

        int cleared = 0;
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                long chunkKey = ChunkUtil.indexChunk(
                        ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z));
                BlockAccessor chunk = world.getChunkIfLoaded(chunkKey);
                if (chunk == null) continue;

                for (int y = fromY; y <= toY; y++) {
                    chunk.setBlock(x, y, z, empty);
                    cleared++;
                }
            }
        }

        LOGGER.atInfo().log("Carved clearing: %d blocks in (%d,%d,%d)-(%d,%d,%d)",
                cleared, fromX, fromY, fromZ, toX, toY, toZ);
    }

    private void setBlockEmpty(World world, int x, int y, int z) {
        long chunkKey = ChunkUtil.indexChunk(
                ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z));
        BlockAccessor chunk = world.getChunkIfLoaded(chunkKey);
        if (chunk == null) return;

        BlockType empty = BlockType.getAssetMap().getAsset("Empty");
        if (empty != null) {
            chunk.setBlock(x, y, z, empty);
        }
    }

    private void deferTicks(World world, int ticks, Runnable action) {
        if (ticks <= 0) {
            world.execute(action);
        } else {
            world.execute(() -> deferTicks(world, ticks - 1, action));
        }
    }

}
