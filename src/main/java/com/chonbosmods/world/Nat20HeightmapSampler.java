package com.chonbosmods.world;

public final class Nat20HeightmapSampler {

    private Nat20HeightmapSampler() {}

    public enum Mode { MIN, MEDIAN, ENTRY_ANCHOR }

    public record SampleResult(int y, int slopeDelta, boolean tooSteep) {}

    static boolean isTreeBlockName(String name) {
        if (name == null || !name.startsWith("Wood_")) return false;
        return name.endsWith("_Log")
            || name.endsWith("_Leaves")
            || name.endsWith("_Foliage")
            || name.endsWith("_Branch");
    }
}
