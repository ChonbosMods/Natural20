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
     * Interpolate with ilvl + quality scaling. The scale factor is
     * {@link com.chonbosmods.progression.Nat20XpMath#ilvlScale(int, int)}, applied
     * uniformly to both endpoints, so the final value is
     * {@code scale × (min + (max-min) × lootLevel)}.
     *
     * <p>See design doc: {@code docs/plans/2026-04-25-affix-ilvl-scaling-and-stat-score-tightening-design.md}.
     */
    public double interpolate(double lootLevel, int ilvl, int qualityValue) {
        double scale = Nat20XpMath.ilvlScale(ilvl, qualityValue);
        double scaledMin = min * scale;
        double scaledMax = max * scale;
        return scaledMin + (scaledMax - scaledMin) * lootLevel;
    }
}
