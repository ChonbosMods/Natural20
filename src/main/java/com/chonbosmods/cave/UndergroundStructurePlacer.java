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
    private static final String TEST_PREFAB_KEY = "Nat20/dungeon/Dungeon2Test";
    private static final String SURFACE_FALLBACK_PREFAB_KEY = "Nat20/dungeon/Dungeon2Test"; // TODO: replace with small surface POI prefab
    private static final int TUNNEL_WIDTH = 3;
    private static final int TUNNEL_HEIGHT = 4;
    private static final int DEFER_TICKS = 5;
    private static final int CARVE_MARGIN = 3;
    private static final int ENTRANCE_HALF_WIDTH = 3;
    private static final int ENTRANCE_UP = 2;
    private static final int ENTRANCE_DOWN = 1;

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

        // 1. Score floor positions for tunnel-mouth placement
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
        int bestScore = -1;
        String bestWallDir = null;

        for (int[] fp : floorPositions) {
            int x = fp[0], y = fp[1], z = fp[2];

            // Skip fluid positions
            BlockType footBlock = world.getBlockType(x, y, z);
            if (footBlock != null && footBlock.getId() != null
                    && footBlock.getId().startsWith("Fluid_")) continue;

            // Ledge filter: check that below the floor is thick solid, not air
            // A real cave floor has solid for several blocks below; a ceiling ledge has air
            int solidBelow = 0;
            for (int dy = y - 1; dy >= y - 5; dy--) {
                BlockType bt = world.getBlockType(x, dy, z);
                if (bt != null && bt.getMaterial() == BlockMaterial.Solid) solidBelow++;
            }
            if (solidBelow < 3) continue;

            // Vertical clearance
            int clearance = 0;
            for (int dy = y; dy < y + 50; dy++) {
                BlockType bt = world.getBlockType(x, dy, z);
                if (bt != null && bt.getMaterial() == BlockMaterial.Solid) break;
                clearance++;
            }
            if (clearance < 4) continue;

            // Air extents in each cardinal direction
            int airNegX = scanAir(world, x, y, z, -1, 0);
            int airPosX = scanAir(world, x, y, z, 1, 0);
            int airNegZ = scanAir(world, x, y, z, 0, -1);
            int airPosZ = scanAir(world, x, y, z, 0, 1);

            int xSpan = airNegX + 1 + airPosX;
            int zSpan = airNegZ + 1 + airPosZ;
            int minSpan = Math.min(xSpan, zSpan);
            int maxSpan = Math.max(xSpan, zSpan);
            int minAir = Math.min(Math.min(airNegX, airPosX), Math.min(airNegZ, airPosZ));

            // Reject: too narrow (crevice) or too open (center of chamber)
            if (minSpan < 3 || maxSpan < 5 || minAir > 6) continue;

            // Wall density in ring 3-5
            int wallBlocks = 0;
            int ringTotal = 0;
            for (int rx = x - 5; rx <= x + 5; rx++) {
                for (int rz = z - 5; rz <= z + 5; rz++) {
                    int dx = Math.abs(rx - x);
                    int dz = Math.abs(rz - z);
                    if (dx < 3 && dz < 3) continue;
                    ringTotal++;
                    for (int ry = y; ry < y + 4; ry++) {
                        BlockType bt = world.getBlockType(rx, ry, rz);
                        if (bt != null && bt.getMaterial() == BlockMaterial.Solid) {
                            wallBlocks++;
                            break;
                        }
                    }
                }
            }
            double wallDensity = ringTotal > 0 ? (double) wallBlocks / ringTotal : 0;
            if (wallDensity < 0.3) continue;

            // Score: prefer close wall + high enclosure + good clearance
            // Lower minAir = closer to wall = better
            int score = (int) (wallDensity * 100) + clearance - minAir * 5;

            // Determine which direction the nearest wall is
            String wallDir;
            if (airNegX <= airPosX && airNegX <= airNegZ && airNegX <= airPosZ) wallDir = "-X";
            else if (airPosX <= airNegZ && airPosX <= airPosZ) wallDir = "+X";
            else if (airNegZ <= airPosZ) wallDir = "-Z";
            else wallDir = "+Z";

            if (score > bestScore) {
                bestScore = score;
                bestFloor = fp;
                bestWallDir = wallDir;
            }
        }

        if (bestFloor == null) {
            LOGGER.atWarning().log("No suitable tunnel-mouth floor in void at (%d, %d, %d)",
                    cx, cy, cz);
            result.complete(null);
            return result;
        }

        int floorX = bestFloor[0];
        int floorY = bestFloor[1];
        int floorZ = bestFloor[2];

        // Determine rotation: entrance faces +Z by default (Dungeon2Test)
        // Rotate so entrance faces AWAY from the nearest wall (into the cave)
        // Wall on -X -> entrance faces +X -> rotate 270 (entrance +Z becomes +X)
        // Wall on +X -> entrance faces -X -> rotate 90  (entrance +Z becomes -X)
        // Wall on -Z -> entrance faces +Z -> no rotation (default)
        // Wall on +Z -> entrance faces -Z -> rotate 180
        Rotation rotation = switch (bestWallDir) {
            case "-X" -> Rotation.TwoSeventy;
            case "+X" -> Rotation.Ninety;
            case "+Z" -> Rotation.OneEighty;
            default -> Rotation.None;  // -Z: entrance already faces +Z, away from -Z wall
        };

        LOGGER.atFine().log("Selected tunnel-mouth at (%d, %d, %d) score=%d wallDir=%s rotation=%s for void at (%d, %d, %d)",
                floorX, floorY, floorZ, bestScore, bestWallDir, rotation, cx, cy, cz);

        // 2. Place with anchor aligned to the tunnel-mouth floor
        int structX = floorX;
        int structY = floorY;
        int structZ = floorZ;

        LOGGER.atFine().log("Placing structure at (%d, %d, %d) rotation=%s for void at (%d, %d, %d)",
                structX, structY, structZ, rotation, cx, cy, cz);

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

        LOGGER.atFine().log("Loaded prefab buffer: %s (size: %dx%dx%d, anchor: %d,%d,%d, columns: %d)",
                prefabPath,
                buffer.getMaxX() - buffer.getMinX(),
                buffer.getMaxY() - buffer.getMinY(),
                buffer.getMaxZ() - buffer.getMinZ(),
                buffer.getAnchorX(), buffer.getAnchorY(), buffer.getAnchorZ(),
                buffer.getColumnCount());

        // PrefabUtil.paste treats position as the anchor location
        Vector3i pastePos = new Vector3i(structX, structY, structZ);

        LOGGER.atFine().log("Paste position (anchor at): (%d, %d, %d) rotation=%s",
                structX, structY, structZ, rotation);

        // 5. Pre-load chunks covering the prefab footprint
        int minChunkX = ChunkUtil.chunkCoordinate(structX + buffer.getMinX());
        int minChunkZ = ChunkUtil.chunkCoordinate(structZ + buffer.getMinZ());
        int maxChunkX = ChunkUtil.chunkCoordinate(structX + buffer.getMaxX());
        int maxChunkZ = ChunkUtil.chunkCoordinate(structZ + buffer.getMaxZ());

        List<CompletableFuture<?>> chunkFutures = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long key = ChunkUtil.indexChunk(chunkX, chunkZ);
                chunkFutures.add(world.getNonTickingChunkAsync(key));
            }
        }

        LOGGER.atFine().log("Pre-loading %d chunks for prefab placement", chunkFutures.size());

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
                            // Carving disabled: Dungeon2Test has its own Empty blocks
                            // carveClearing(world, pasteX, pasteY, pasteZ, buffer);

                            Random random = new Random();
                            PrefabUtil.paste(buffer, world, pastePos, rotation, true, random, 0, store);
                            LOGGER.atFine().log("Pasted prefab at (%d, %d, %d)", structX, structY, structZ);

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
        return findPrefabPath(TEST_PREFAB_KEY);
    }

    private Path findSurfaceFallbackPrefabPath() {
        return findPrefabPath(SURFACE_FALLBACK_PREFAB_KEY);
    }

    private Path findPrefabPath(String prefabKey) {
        // Try asset pack lookup first
        Path assetPath = PrefabStore.get().findAssetPrefabPath(prefabKey);
        if (assetPath != null) {
            return assetPath;
        }

        // Fall back: resolve from plugin file path (dev mode)
        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile != null) {
            Path candidate = pluginFile;
            for (int i = 0; i < 4; i++) {
                Path assetsDir = candidate.resolve("assets").resolve("Server").resolve("Prefabs")
                        .resolve(prefabKey + ".prefab.json");
                if (Files.exists(assetsDir)) {
                    LOGGER.atFine().log("Found prefab via fallback path: %s", assetsDir);
                    return assetsDir;
                }
                Path directDir = candidate.resolve("Server").resolve("Prefabs")
                        .resolve(prefabKey + ".prefab.json");
                if (Files.exists(directDir)) {
                    LOGGER.atFine().log("Found prefab via direct path: %s", directDir);
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

        LOGGER.atFine().log("Carved tunnel from (%d, %d, %d) to (%d, %d, %d): %d steps",
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

        // Entrance exclusion zone: anchor world position ± half-width
        // Detect entrance face from anchor position relative to prefab bounds
        int anchorWorldX = pasteX + buffer.getAnchorX();
        int anchorWorldY = pasteY + buffer.getAnchorY();
        int anchorWorldZ = pasteZ + buffer.getAnchorZ();
        boolean entranceOnMaxZ = buffer.getAnchorZ() >= buffer.getMaxZ();
        int entranceFaceZ = entranceOnMaxZ
                ? pasteZ + buffer.getMaxZ()
                : pasteZ + buffer.getMinZ();

        BlockType empty = BlockType.getAssetMap().getAsset("Empty");
        if (empty == null) {
            LOGGER.atSevere().log("Could not find Empty block type: carving skipped");
            return;
        }

        int cleared = 0;
        int skipped = 0;
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                long chunkKey = ChunkUtil.indexChunk(
                        ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z));
                BlockAccessor chunk = world.getChunkIfLoaded(chunkKey);
                if (chunk == null) continue;

                for (int y = fromY; y <= toY; y++) {
                    // Skip entrance zone: in front of the entrance face, within entrance dimensions
                    boolean inEntranceZ = entranceOnMaxZ ? z >= entranceFaceZ : z <= entranceFaceZ;
                    if (inEntranceZ
                            && x >= anchorWorldX - ENTRANCE_HALF_WIDTH
                            && x <= anchorWorldX + ENTRANCE_HALF_WIDTH
                            && y >= anchorWorldY - ENTRANCE_DOWN
                            && y <= anchorWorldY + ENTRANCE_UP) {
                        skipped++;
                        continue;
                    }
                    chunk.setBlock(x, y, z, empty);
                    cleared++;
                }
            }
        }

        LOGGER.atFine().log("Carved clearing: %d blocks cleared, %d skipped (entrance zone) in (%d,%d,%d)-(%d,%d,%d)",
                cleared, skipped, fromX, fromY, fromZ, toX, toY, toZ);
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

    private int scanAir(World world, int x, int y, int z, int dx, int dz) {
        int count = 0;
        int cx = x + dx;
        int cz = z + dz;
        while (count < 50) {
            BlockType bt = world.getBlockType(cx, y, cz);
            if (bt != null && bt.getMaterial() == BlockMaterial.Solid) break;
            count++;
            cx += dx;
            cz += dz;
        }
        return count;
    }

    /**
     * Place the test prefab at surface level at the given x/z position.
     * Finds the surface Y by scanning downward, then pastes with no rotation.
     *
     * @return a future that completes with the surface position, or null on failure
     */
    public CompletableFuture<Vector3i> placeAtSurface(World world, int targetX, int targetZ,
                                                        Store<EntityStore> store) {
        CompletableFuture<Vector3i> result = new CompletableFuture<>();

        Path prefabPath = findSurfaceFallbackPrefabPath();
        if (prefabPath == null) {
            LOGGER.atSevere().log("Surface placement: surfaceFallbackPrefab not found: %s", SURFACE_FALLBACK_PREFAB_KEY);
            result.complete(null);
            return result;
        }

        IPrefabBuffer buffer;
        try {
            buffer = PrefabBufferUtil.getCached(prefabPath);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Surface placement: failed to load prefab buffer");
            result.complete(null);
            return result;
        }

        // Pre-load the chunk at target position to scan surface height
        long targetChunkKey = ChunkUtil.indexChunk(
            ChunkUtil.chunkCoordinate(targetX), ChunkUtil.chunkCoordinate(targetZ));
        world.getNonTickingChunkAsync(targetChunkKey)
            .orTimeout(30, TimeUnit.SECONDS)
            .whenComplete((chunk, err) -> {
                if (err != null) {
                    LOGGER.atWarning().withCause(err).log("Surface placement: chunk load failed");
                    result.complete(null);
                    return;
                }
                world.execute(() -> {
                    // Find surface Y: scan downward from Y=200 to first solid block
                    int foundY = -1;
                    for (int y = 200; y > 10; y--) {
                        BlockType bt = world.getBlockType(targetX, y, targetZ);
                        if (bt != null && bt.getMaterial() == BlockMaterial.Solid) {
                            foundY = y + 1; // place ON TOP of the solid block
                            break;
                        }
                    }
                    if (foundY < 0) {
                        LOGGER.atWarning().log("Surface placement: no solid ground at (%d, %d)", targetX, targetZ);
                        result.complete(null);
                        return;
                    }

                    final int surfaceY = foundY;
                    Vector3i pastePos = new Vector3i(targetX, surfaceY, targetZ);

                    // Pre-load all chunks covering the prefab footprint
                    int minCX = ChunkUtil.chunkCoordinate(targetX + buffer.getMinX());
                    int minCZ = ChunkUtil.chunkCoordinate(targetZ + buffer.getMinZ());
                    int maxCX = ChunkUtil.chunkCoordinate(targetX + buffer.getMaxX());
                    int maxCZ = ChunkUtil.chunkCoordinate(targetZ + buffer.getMaxZ());

                    List<CompletableFuture<?>> chunkFutures = new ArrayList<>();
                    for (int cx = minCX; cx <= maxCX; cx++) {
                        for (int cz = minCZ; cz <= maxCZ; cz++) {
                            chunkFutures.add(world.getNonTickingChunkAsync(ChunkUtil.indexChunk(cx, cz)));
                        }
                    }

                    CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                        .orTimeout(30, TimeUnit.SECONDS)
                        .whenComplete((ignored, error) -> {
                            if (error != null) {
                                LOGGER.atSevere().withCause(error).log("Surface placement: chunk loading timed out");
                                result.complete(null);
                                return;
                            }
                            deferTicks(world, DEFER_TICKS, () -> {
                                try {
                                    Random random = new Random();
                                    PrefabUtil.paste(buffer, world, pastePos, Rotation.None, true, random, 0, store);
                                    LOGGER.atFine().log("Surface POI placed at (%d, %d, %d)", targetX, surfaceY, targetZ);
                                    result.complete(pastePos);
                                } catch (Exception e) {
                                    LOGGER.atSevere().withCause(e).log("Surface placement: paste failed");
                                    result.complete(null);
                                }
                            });
                        });
                });
            });

        return result;
    }

    private void deferTicks(World world, int ticks, Runnable action) {
        if (ticks <= 0) {
            world.execute(action);
        } else {
            world.execute(() -> deferTicks(world, ticks - 1, action));
        }
    }

}
