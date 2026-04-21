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
}
