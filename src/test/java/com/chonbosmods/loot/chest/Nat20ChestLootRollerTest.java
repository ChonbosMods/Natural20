package com.chonbosmods.loot.chest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Nat20ChestLootRollerTest {

    private Nat20ChestLootConfig cfgWith(Path tmp, double primary, double secondary) throws Exception {
        Path file = tmp.resolve("chest_loot.json");
        Files.writeString(file, """
                { "primary_chance": %s, "secondary_chance": %s, "chest_block_types": [] }
                """.formatted(primary, secondary));
        return Nat20ChestLootConfig.load(file);
    }

    @Test
    void primaryAlwaysTrueWhenChanceIsOne(@TempDir Path tmp) throws Exception {
        Nat20ChestLootRoller roller = new Nat20ChestLootRoller(cfgWith(tmp, 1.0, 0.0));
        Random rng = new Random(1234L);
        for (int i = 0; i < 100; i++) assertTrue(roller.rollPrimary(rng));
    }

    @Test
    void primaryAlwaysFalseWhenChanceIsZero(@TempDir Path tmp) throws Exception {
        Nat20ChestLootRoller roller = new Nat20ChestLootRoller(cfgWith(tmp, 0.0, 1.0));
        Random rng = new Random(1234L);
        for (int i = 0; i < 100; i++) assertFalse(roller.rollPrimary(rng));
    }

    @Test
    void secondaryAlwaysTrueWhenChanceIsOne(@TempDir Path tmp) throws Exception {
        Nat20ChestLootRoller roller = new Nat20ChestLootRoller(cfgWith(tmp, 0.0, 1.0));
        Random rng = new Random(1234L);
        for (int i = 0; i < 100; i++) assertTrue(roller.rollSecondary(rng));
    }

    @Test
    void secondaryAlwaysFalseWhenChanceIsZero(@TempDir Path tmp) throws Exception {
        Nat20ChestLootRoller roller = new Nat20ChestLootRoller(cfgWith(tmp, 1.0, 0.0));
        Random rng = new Random(1234L);
        for (int i = 0; i < 100; i++) assertFalse(roller.rollSecondary(rng));
    }

    @Test
    void primaryAndSecondaryUseIndependentChances(@TempDir Path tmp) throws Exception {
        // Random(1234L).nextDouble() first value ~= 0.6466, second ~= 0.9513
        Nat20ChestLootConfig cfg = cfgWith(tmp, 0.7, 0.1);
        Random rng = new Random(1234L);
        Nat20ChestLootRoller roller = new Nat20ChestLootRoller(cfg);
        assertTrue(roller.rollPrimary(rng), "0.6466 < 0.7 -> true");
        assertFalse(roller.rollSecondary(rng), "0.9513 < 0.1 -> false");
    }
}
