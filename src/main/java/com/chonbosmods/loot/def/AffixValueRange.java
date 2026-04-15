package com.chonbosmods.loot.def;

import com.chonbosmods.progression.Nat20XpMath;

public record AffixValueRange(double min, double max) {

    /**
     * Interpolate without ilvl scaling (legacy callers that don't need scaling).
     * Prefer the 3-arg overload at any read site that has lootData in scope.
     */
    public double interpolate(double lootLevel) {
        return min + (max - min) * lootLevel;
    }

    /**
     * Interpolate with ilvl + quality scaling per design doc §11.5:
     * {@code scale = 1 + (ilvl-1) * (0.025 + (qualityValue-1) * 0.003)}.
     * Applied uniformly to both endpoints, so the final value is
     * {@code scale * (min + (max-min) * lootLevel)}.
     */
    public double interpolate(double lootLevel, int ilvl, int qualityValue) {
        double scale = Nat20XpMath.ilvlScale(ilvl, qualityValue);
        double scaledMin = min * scale;
        double scaledMax = max * scale;
        return scaledMin + (scaledMax - scaledMin) * lootLevel;
    }
}
