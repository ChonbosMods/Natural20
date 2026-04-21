package com.chonbosmods.loot.chest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Nat20ChestLootRollerTest {

    private Nat20ChestLootConfig cfgWithChance(Path tmp, double chance) throws Exception {
        Path file = tmp.resolve("chest_loot.json");
        Files.writeString(file, """
                { "chance": %s, "chest_block_types": [] }
                """.formatted(chance));
        return Nat20ChestLootConfig.load(file);
    }

    @Test
    void rollAlwaysTrueWhenChanceIsOne(@TempDir Path tmp) throws Exception {
        Nat20ChestLootConfig cfg = cfgWithChance(tmp, 1.0);
        Random rng = new Random(1234L);
        Nat20ChestLootRoller roller = new Nat20ChestLootRoller(cfg);
        for (int i = 0; i < 100; i++) {
            assertTrue(roller.roll(rng), "chance=1.0 must always fire");
        }
    }

    @Test
    void rollAlwaysFalseWhenChanceIsZero(@TempDir Path tmp) throws Exception {
        Nat20ChestLootConfig cfg = cfgWithChance(tmp, 0.0);
        Random rng = new Random(1234L);
        Nat20ChestLootRoller roller = new Nat20ChestLootRoller(cfg);
        for (int i = 0; i < 100; i++) {
            assertFalse(roller.roll(rng), "chance=0.0 must never fire");
        }
    }

    @Test
    void rollReturnsTrueWhenRngBelowChance(@TempDir Path tmp) throws Exception {
        Nat20ChestLootConfig cfg = cfgWithChance(tmp, 0.7);
        // Random(1234L).nextDouble() first value ~= 0.6466, below 0.7
        Random rng = new Random(1234L);
        assertTrue(new Nat20ChestLootRoller(cfg).roll(rng));
    }
}
