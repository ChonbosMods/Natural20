package com.chonbosmods.loot.chest;

import java.util.Random;

public final class Nat20ChestLootRoller {

    private final Nat20ChestLootConfig config;

    public Nat20ChestLootRoller(Nat20ChestLootConfig config) {
        this.config = config;
    }

    public boolean roll(Random rng) {
        return rng.nextDouble() < config.getChance();
    }
}
