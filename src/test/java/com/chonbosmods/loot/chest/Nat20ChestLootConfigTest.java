package com.chonbosmods.loot.chest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Nat20ChestLootConfigTest {

    @Test
    void loadsChancePerBand(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("chest_loot.json");
        Files.writeString(cfg, """
                {
                  "chance_per_band": [0.05, 0.10, 0.15, 0.25],
                  "default_chance": 0.25,
                  "chest_block_types": ["Furniture_Chest", "hytale:chest"]
                }
                """);
        Nat20ChestLootConfig c = Nat20ChestLootConfig.load(cfg);
        assertEquals(0.05, c.chanceForBand(0), 1e-9);
        assertEquals(0.10, c.chanceForBand(1), 1e-9);
        assertEquals(0.15, c.chanceForBand(2), 1e-9);
        assertEquals(0.25, c.chanceForBand(3), 1e-9);
    }

    @Test
    void fallsBackToDefaultForOutOfRangeBand(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("chest_loot.json");
        Files.writeString(cfg, """
                {
                  "chance_per_band": [0.05, 0.10, 0.15, 0.25],
                  "default_chance": 0.40,
                  "chest_block_types": []
                }
                """);
        Nat20ChestLootConfig c = Nat20ChestLootConfig.load(cfg);
        assertEquals(0.40, c.chanceForBand(99), 1e-9);
        assertEquals(0.40, c.chanceForBand(-1), 1e-9);
    }

    @Test
    void matchesChestBlockTypeCaseSensitive(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("chest_loot.json");
        Files.writeString(cfg, """
                {
                  "chance_per_band": [0.05],
                  "default_chance": 0.05,
                  "chest_block_types": ["Furniture_Chest", "hytale:chest"]
                }
                """);
        Nat20ChestLootConfig c = Nat20ChestLootConfig.load(cfg);
        assertTrue(c.isChestBlock("Furniture_Chest"));
        assertTrue(c.isChestBlock("hytale:chest"));
        assertFalse(c.isChestBlock("Furniture_Chest_Epic"));
        assertFalse(c.isChestBlock("furniture_chest"));
    }
}
