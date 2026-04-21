package com.chonbosmods.world;

public final class Nat20HeightmapSampler {

    private Nat20HeightmapSampler() {}

    public enum Mode { MIN, MEDIAN, ENTRY_ANCHOR }

    public record SampleResult(int y, int slopeDelta, boolean tooSteep) {}

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
     * {@code maxSteps}.
     */
    static int walkDownToSolidGround(int startY,
                                     int maxSteps,
                                     java.util.function.IntFunction<String> blockNameAt,
                                     java.util.function.IntPredicate isSolidAt) {
        for (int step = 0; step < maxSteps; step++) {
            int y = startY - step;
            if (y <= 0) return 0;
            String name = blockNameAt.apply(y);
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
}
