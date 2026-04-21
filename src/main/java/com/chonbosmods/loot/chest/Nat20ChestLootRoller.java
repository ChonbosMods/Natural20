package com.chonbosmods.loot.chest;

import java.util.Random;

public final class Nat20ChestLootRoller {

    private final Nat20ChestLootConfig config;

    public Nat20ChestLootRoller(Nat20ChestLootConfig config) {
        this.config = config;
    }

    public boolean roll(int areaLevel, Random rng) {
        double chance = config.chanceForBand(bandForAreaLevel(areaLevel));
        return rng.nextDouble() < chance;
    }

    public static int bandForAreaLevel(int areaLevel) {
        if (areaLevel <= 10) return 0;
        if (areaLevel <= 20) return 1;
        if (areaLevel <= 30) return 2;
        return 3;
    }
}
