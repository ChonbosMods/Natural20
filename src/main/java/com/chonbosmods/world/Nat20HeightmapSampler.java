package com.chonbosmods.world;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

public final class Nat20HeightmapSampler {

    private Nat20HeightmapSampler() {}

    public enum Mode { MIN, MEDIAN, ENTRY_ANCHOR }

    public record SampleResult(
        int y,
        int slopeDelta,
        boolean tooSteep,
        int maxSubmergedDepth,
        boolean tooWet
    ) {}

    private static final int DOWNWALK_MAX_STEPS = 20;
    private static final int DEFAULT_SLOPE_THRESHOLD = 4;

    static boolean isTreeBlockName(String name) {
        if (name == null) return false;
        if (name.startsWith("Prototype_")) return false;
        if (name.startsWith("Plant_Leaves_")) return true;
        if (!name.startsWith("Wood_")) return false;
        return name.contains("_Trunk") || name.contains("_Branch_") || name.endsWith("_Roots");
    }

    /**
     * Walks down from {@code startY} skipping tree blocks (leaves, trunks, branches, roots)
     * until a non-tree solid block is found. Returns (solid block Y + 1), i.e., the first
     * buildable Y above ground. Returns 0 (sentinel) if no solid block found within
     * {@code maxSteps}, or if any fluid (water/lava/etc.) is encountered.
     *
     * <p>Fluids are tested two ways: primarily via {@code isFluidAt} (Hytale's fluid layer
     * lives in a separate {@code fluidId} palette, not in {@code blockId}), and defensively
     * via block-name prefix {@code "Fluid_"} for servers that may stamp fluids as blocks.
     */
    static int walkDownToSolidGround(int startY,
                                     int maxSteps,
                                     java.util.function.IntFunction<String> blockNameAt,
                                     java.util.function.IntPredicate isSolidAt,
                                     java.util.function.IntPredicate isFluidAt) {
        for (int step = 0; step < maxSteps; step++) {
            int y = startY - step;
            if (y <= 0) return 0;
            if (isFluidAt.test(y)) return 0;
            String name = blockNameAt.apply(y);
            if (name != null && name.startsWith("Fluid_")) return 0;
            if (isTreeBlockName(name)) continue;
            if (isSolidAt.test(y)) return y + 1;
        }
        return 0;
    }

    /**
     * Reduces the 5 probe heights (corners + center) to a single anchor Y according to {@code mode}.
     *
     * <ul>
     *   <li>{@link Mode#MIN}: lowest of all probes. Sits the prefab on the lowest corner so it
     *       never floats over a dip; higher corners clip into terrain.</li>
     *   <li>{@link Mode#MEDIAN}: middle of the sorted probes. Accepts some corner clipping on
     *       uneven terrain in exchange for not burying the prefab at the lowest pit.</li>
     *   <li>{@link Mode#ENTRY_ANCHOR}: returns {@code heights[0]}. The caller is expected to pass
     *       the entry-point XZ as the first probe so the entry is flush with terrain; the
     *       remaining probes are used only for slope reporting, not Y selection.</li>
     * </ul>
     */
    static int reduce(int[] heights, Mode mode) {
        return switch (mode) {
            case MIN -> {
                int m = Integer.MAX_VALUE;
                for (int h : heights) if (h < m) m = h;
                yield m;
            }
            case MEDIAN -> {
                int[] copy = heights.clone();
                java.util.Arrays.sort(copy);
                yield copy[copy.length / 2];
            }
            case ENTRY_ANCHOR -> heights[0];
        };
    }

    static int slopeDelta(int[] heights) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int h : heights) {
            if (h < min) min = h;
            if (h > max) max = h;
        }
        return max - min;
    }

    /**
     * Sample the terrain surface at an axis-aligned footprint centered on (centerX, centerZ).
     * Probes 5 points (center + 4 corners) via WorldChunk.getHeight(), walks each result down
     * through tree blocks to real solid ground, then reduces per {@code mode}.
     *
     * <p><b>Chunk requirement:</b> All 5 probe points must lie in non-ticking-loaded chunks.
     * Callers typically preload chunks via {@code world.getNonTickingChunkAsync(...)} before
     * invoking this method.
     *
     * <p><b>ENTRY_ANCHOR mode:</b> Y is taken from the center probe; corner probes contribute
     * only to slope reporting. Use when a specific XZ (dungeon mouth, door sill) must be flush
     * with terrain and the rest of the footprint can clip.
     *
     * @return sampled Y + slope delta, or a result with {@code tooSteep=true} if the delta
     *         exceeds {@code slopeThreshold}.
     */
    public static SampleResult sample(World world,
                                      int centerX, int centerZ,
                                      int halfX, int halfZ,
                                      Mode mode,
                                      int slopeThreshold) {
        int[] xs = { centerX, centerX - halfX, centerX + halfX, centerX - halfX, centerX + halfX };
        int[] zs = { centerZ, centerZ - halfZ, centerZ - halfZ, centerZ + halfZ, centerZ + halfZ };
        int[] heights = new int[5];
        for (int i = 0; i < 5; i++) {
            heights[i] = probeGroundY(world, xs[i], zs[i]);
        }
        int delta = slopeDelta(heights);
        int y = reduce(heights, mode);
        return new SampleResult(y, delta, delta > slopeThreshold, 0, false);
    }

    /** Convenience overload: uses the default slope threshold ({@value DEFAULT_SLOPE_THRESHOLD}). */
    public static SampleResult sample(World world,
                                      int centerX, int centerZ,
                                      int halfX, int halfZ,
                                      Mode mode) {
        return sample(world, centerX, centerZ, halfX, halfZ, mode, DEFAULT_SLOPE_THRESHOLD);
    }

    /**
     * Counts contiguous fluid cells starting AT {@code groundY} and walking up. Stops at the first
     * non-fluid cell, at {@code groundY + maxDepth}, or at {@code canopyY + 1}, whichever comes first.
     *
     * <p>Used by {@link #probeGroundY} to detect submersion. The walker that produces {@code groundY}
     * starts at {@link WorldChunk#getHeight} and walks DOWN, but the heightmap skips Empty cells
     * (Hytale's fluid cells are blockId=Empty with fluidId in a separate palette), so the walker
     * lands on the seabed below an ocean column and the in-walk fluid check never fires. This upward
     * scan covers that gap: after grounding, look up for fluid that the heightmap stepped over.
     *
     * @param groundY first buildable Y above solid (the value returned by walkDownToSolidGround)
     * @param canopyY chunk heightmap value, inclusive upper bound on the scan
     * @param maxDepth cap on the returned count; caller passes wetThreshold + 1 to short-circuit
     * @param isFluidAt fluid predicate (chunk.getFluidId(lx, y, lz) != 0)
     * @return contiguous fluid count in [0, maxDepth]
     */
    static int scanFluidDepthAbove(int groundY,
                                   int canopyY,
                                   int maxDepth,
                                   java.util.function.IntPredicate isFluidAt) {
        int depth = 0;
        for (int y = groundY; y < groundY + maxDepth && y <= canopyY; y++) {
            if (isFluidAt.test(y)) depth++;
            else break;
        }
        return depth;
    }

    /** Probe a single XZ: get canopy Y from the chunk heightmap, walk down past trees. */
    private static int probeGroundY(World world, int x, int z) {
        long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getNonTickingChunk(chunkKey);
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, ChunkUtil.SIZE);
        int lz = Math.floorMod(z, ChunkUtil.SIZE);
        int canopyY = chunk.getHeight(lx, lz);
        if (canopyY <= 0) return 0;

        // Single-slot cache so blockNameAt and isSolidAt share one BlockType lookup per Y.
        // The walk always calls blockNameAt(y) first, then isSolidAt(y) on the same y — so
        // we prime on the name call and reuse on the solid check.
        int[] cachedY = { Integer.MIN_VALUE };
        BlockType[] cachedBt = { null };
        java.util.function.IntFunction<BlockType> lookup = y -> {
            if (cachedY[0] != y) {
                cachedY[0] = y;
                cachedBt[0] = world.getBlockType(x, y, z);
            }
            return cachedBt[0];
        };

        return walkDownToSolidGround(canopyY, DOWNWALK_MAX_STEPS,
            y -> {
                BlockType bt = lookup.apply(y);
                return bt == null ? "" : bt.getId();
            },
            y -> {
                BlockType bt = lookup.apply(y);
                return bt != null && bt.getMaterial() == BlockMaterial.Solid;
            },
            // Hytale tracks fluids in a separate palette on the chunk, not as blockIds:
            // water cells have blockId=Empty but fluidId > 0. Without this check the walk
            // would descend through water and land on the seabed, sinking prefabs.
            y -> chunk.getFluidId(lx, y, lz) != 0);
    }
}
