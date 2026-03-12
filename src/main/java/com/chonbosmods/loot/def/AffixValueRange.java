package com.chonbosmods.loot.def;

public record AffixValueRange(double min, double max) {
    public double interpolate(double lootLevel) {
        return min + (max - min) * lootLevel;
    }
}
