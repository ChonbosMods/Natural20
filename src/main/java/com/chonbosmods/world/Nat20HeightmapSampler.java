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
     * Walks down from {@code startY} skipping tree blocks (logs/leaves/foliage/branches)
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
}
