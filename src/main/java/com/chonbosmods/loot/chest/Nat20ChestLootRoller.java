package com.chonbosmods.loot.chest;

import java.util.Random;

public final class Nat20ChestLootRoller {

    private final Nat20ChestLootConfig config;

    public Nat20ChestLootRoller(Nat20ChestLootConfig config) {
        this.config = config;
    }

    public boolean rollPrimary(Random rng) {
        return rng.nextDouble() < config.getPrimaryChance();
    }

    /** Conditional roll for a bonus second item; caller should only invoke when primary succeeded. */
    public boolean rollSecondary(Random rng) {
        return rng.nextDouble() < config.getSecondaryChance();
    }
}
