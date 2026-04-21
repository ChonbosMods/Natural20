package com.chonbosmods.world;

public final class Nat20HeightmapSampler {

    private Nat20HeightmapSampler() {}

    public enum Mode { MIN, MEDIAN, ENTRY_ANCHOR }

    public record SampleResult(int y, int slopeDelta, boolean tooSteep) {}
}
