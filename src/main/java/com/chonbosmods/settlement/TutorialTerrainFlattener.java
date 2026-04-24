package com.chonbosmods.settlement;

import com.chonbosmods.loot.chest.Nat20ChestContainerWriter;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Guaranteed terrain flattener for the tutorial (spawn-cell) settlement. Clears
 * a column of blocks above the target Y across a square pad, lays a flat grass
 * surface, and fills a few dirt blocks below so the pad doesn't float. The
 * {@link SettlementPieceAssembler} then sees uniform terrain and every piece
 * grounds successfully. Used only by the spawn cell; non-spawn cells keep the
 * existing non-destructive terrain-sampling flow.
 */
public final class TutorialTerrainFlattener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|TutorialFlattener");

    private static final String BLOCK_GRASS = "Soil_Grass";
    private static final String BLOCK_DIRT = "Soil_Dirt";
    private static final String BLOCK_EMPTY = "Empty";

    /** Blocks of headroom cleared above the pad surface. Covers tree canopies + overhangs. */
    private static final int CLEAR_HEIGHT = 20;

    /** Dirt column under the grass surface so pieces aren't pasted over floating voids. */
    private static final int DIRT_DEPTH = 3;

    /** SettlementPieceAssembler passes flags 7 and 93 for its two-pass hydration of
     *  stateful blocks. Our surface is stateless (grass, dirt, empty), so flag 7
     *  alone ("skip state rehydrate") is enough and twice as fast. */
    private static final int SET_BLOCK_FLAGS = 7;

    private TutorialTerrainFlattener() {}

    /**
     * Flatten a square pad of {@code (2*radius + 1)} blocks per side, centered on
     * {@code (centerX, centerY, centerZ)}. Preloads every chunk the pad touches,
     * then on the world thread clears + lays the surface. Returns a future that
     * completes when the flatten finishes (or exceptionally on preload failure).
     */
    public static CompletableFuture<Void> flattenPad(World world,
                                                     int centerX, int centerY, int centerZ,
                                                     int radius) {
        int minCX = ChunkUtil.chunkCoordinate(centerX - radius);
        int maxCX = ChunkUtil.chunkCoordinate(centerX + radius);
        int minCZ = ChunkUtil.chunkCoordinate(centerZ - radius);
        int maxCZ = ChunkUtil.chunkCoordinate(centerZ + radius);

        List<CompletableFuture<?>> preloads = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                preloads.add(world.getNonTickingChunkAsync(ChunkUtil.indexChunk(cx, cz)));
            }
        }

        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture.allOf(preloads.toArray(new CompletableFuture[0]))
            .whenComplete((ignored, err) -> {
                if (err != null) {
                    LOGGER.atWarning().withCause(err).log(
                        "Flatten preload failed at (%d,%d,%d) r=%d", centerX, centerY, centerZ, radius);
                    done.completeExceptionally(err);
                    return;
                }
                world.execute(() -> {
                    try {
                        doFlatten(world, centerX, centerY, centerZ, radius);
                        done.complete(null);
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log(
                            "Flatten failed at (%d,%d,%d) r=%d", centerX, centerY, centerZ, radius);
                        done.completeExceptionally(e);
                    }
                });
            });
        return done;
    }

    private static void doFlatten(World world, int centerX, int centerY, int centerZ, int radius) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        BlockTypeAssetMap assetMap = (BlockTypeAssetMap) BlockType.getAssetMap();
        int grassId = BlockType.getBlockIdOrUnknown(assetMap, BLOCK_GRASS,
            "Flatten: block '%s' not found", new Object[]{BLOCK_GRASS});
        int dirtId  = BlockType.getBlockIdOrUnknown(assetMap, BLOCK_DIRT,
            "Flatten: block '%s' not found", new Object[]{BLOCK_DIRT});
        int emptyId = BlockType.getBlockIdOrUnknown(assetMap, BLOCK_EMPTY,
            "Flatten: block '%s' not found", new Object[]{BLOCK_EMPTY});
        BlockType grass = (BlockType) BlockType.getAssetMap().getAsset(grassId);
        BlockType dirt  = (BlockType) BlockType.getAssetMap().getAsset(dirtId);
        BlockType empty = (BlockType) BlockType.getAssetMap().getAsset(emptyId);
        if (grass == null || dirt == null || empty == null) {
            throw new IllegalStateException(
                "Flatten: missing block types (grass=" + grass + ", dirt=" + dirt + ", empty=" + empty + ")");
        }

        int grassCells = 0;
        int dirtCells  = 0;
        int clearedCells = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            int x = centerX + dx;
            for (int dz = -radius; dz <= radius; dz++) {
                int z = centerZ + dz;
                WorldChunk chunk = Nat20ChestContainerWriter.getLoadedChunk(world, x, z);
                if (chunk == null) continue;

                // Clear above the pad so trees / overhangs don't collide with pieces.
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    chunk.setBlock(x, centerY + dy, z, emptyId, empty, 0, 0, SET_BLOCK_FLAGS);
                    clearedCells++;
                }
                // Lay the grass surface.
                chunk.setBlock(x, centerY, z, grassId, grass, 0, 0, SET_BLOCK_FLAGS);
                grassCells++;
                // Pack dirt below so low terrain pockets don't leave the pad floating
                // over air when pieces paste.
                for (int dy = 1; dy <= DIRT_DEPTH; dy++) {
                    chunk.setBlock(x, centerY - dy, z, dirtId, dirt, 0, 0, SET_BLOCK_FLAGS);
                    dirtCells++;
                }
            }
        }

        LOGGER.atInfo().log(
            "Flattened pad center=(%d,%d,%d) r=%d (grass=%d, dirt=%d, cleared=%d)",
            centerX, centerY, centerZ, radius, grassCells, dirtCells, clearedCells);
    }
}
