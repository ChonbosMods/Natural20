package com.chonbosmods.loot.chest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Nat20ChestLootConfigTest {

    @Test
    void loadsPrimaryAndSecondaryChances(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("chest_loot.json");
        Files.writeString(cfg, """
                {
                  "primary_chance": 0.35,
                  "secondary_chance": 0.1429,
                  "secondary_low_rarity_bias": 0.7,
                  "chest_block_types": ["Furniture_Chest"]
                }
                """);
        Nat20ChestLootConfig c = Nat20ChestLootConfig.load(cfg);
        assertEquals(0.35, c.getPrimaryChance(), 1e-9);
        assertEquals(0.1429, c.getSecondaryChance(), 1e-9);
        assertEquals(0.7, c.getSecondaryLowRarityBias(), 1e-9);
    }

    @Test
    void missingFieldsDefaultSafely(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("chest_loot.json");
        Files.writeString(cfg, """
                { "chest_block_types": [] }
                """);
        Nat20ChestLootConfig c = Nat20ChestLootConfig.load(cfg);
        assertEquals(0.0, c.getPrimaryChance(), 1e-9);
        assertEquals(0.0, c.getSecondaryChance(), 1e-9);
        assertEquals(0.0, c.getSecondaryLowRarityBias(), 1e-9);
    }

    @Test
    void matchesChestBlockTypeCaseSensitive(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("chest_loot.json");
        Files.writeString(cfg, """
                {
                  "primary_chance": 1.0,
                  "secondary_chance": 0.0,
                  "chest_block_types": ["Furniture_Chest", "hytale:chest"]
                }
                """);
        Nat20ChestLootConfig c = Nat20ChestLootConfig.load(cfg);
        assertTrue(c.isChestBlock("Furniture_Chest"));
        assertTrue(c.isChestBlock("hytale:chest"));
        assertFalse(c.isChestBlock("Furniture_Chest_Epic"));
        assertFalse(c.isChestBlock("furniture_chest"));
    }

    @Test
    void matchesStateMachineVariantIds(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("chest_loot.json");
        Files.writeString(cfg, """
                {
                  "primary_chance": 1.0,
                  "secondary_chance": 0.0,
                  "chest_block_types": ["Furniture_Kweebec_Chest_Small", "Furniture_Dungeon_Chest_Legendary_Large"]
                }
                """);
        Nat20ChestLootConfig c = Nat20ChestLootConfig.load(cfg);
        assertTrue(c.isChestBlock("*Furniture_Kweebec_Chest_Small_State_Definitions_CloseWindow"));
        assertTrue(c.isChestBlock("*Furniture_Kweebec_Chest_Small_State_Definitions_OpenWindow"));
        assertTrue(c.isChestBlock("**Furniture_Dungeon_Chest_Legendary_Large_State_Definitions_OpenWindow_State_Definitions_CloseWindow"));
        assertTrue(c.isChestBlock("Furniture_Kweebec_Chest_Small"));
        assertFalse(c.isChestBlock(null));
        assertFalse(c.isChestBlock("*Furniture_NotAChest_State_Definitions_CloseWindow"));
    }
}
