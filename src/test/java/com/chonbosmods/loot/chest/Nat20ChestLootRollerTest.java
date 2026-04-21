package com.chonbosmods.loot.chest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Nat20ChestLootRollerTest {

    private Nat20ChestLootConfig cfgWith(Path tmp, double... chances) throws Exception {
        Path file = tmp.resolve("chest_loot.json");
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < chances.length; i++) {
            if (i > 0) arr.append(",");
            arr.append(chances[i]);
        }
        arr.append("]");
        Files.writeString(file, """
                { "chance_per_band": %s, "default_chance": 0.0, "chest_block_types": [] }
                """.formatted(arr));
        return Nat20ChestLootConfig.load(file);
    }

    @Test
    void bandIndexZeroForAreaLevels1To10(@TempDir Path tmp) throws Exception {
        Nat20ChestLootConfig cfg = cfgWith(tmp, 0.05, 0.10, 0.15, 0.25);
        assertEquals(0, Nat20ChestLootRoller.bandForAreaLevel(1));
        assertEquals(0, Nat20ChestLootRoller.bandForAreaLevel(10));
        assertEquals(1, Nat20ChestLootRoller.bandForAreaLevel(11));
        assertEquals(1, Nat20ChestLootRoller.bandForAreaLevel(20));
        assertEquals(2, Nat20ChestLootRoller.bandForAreaLevel(21));
        assertEquals(3, Nat20ChestLootRoller.bandForAreaLevel(31));
        assertEquals(3, Nat20ChestLootRoller.bandForAreaLevel(40));
    }

    @Test
    void rollReturnsTrueBelowThreshold(@TempDir Path tmp) throws Exception {
        Nat20ChestLootConfig cfg = cfgWith(tmp, 0.7);
        Random rng = new Random(1234L);
        assertTrue(new Nat20ChestLootRoller(cfg).roll(1, rng));
    }

    @Test
    void rollReturnsFalseAboveThreshold(@TempDir Path tmp) throws Exception {
        Nat20ChestLootConfig cfg = cfgWith(tmp, 0.0);
        Random rng = new Random(1234L);
        assertFalse(new Nat20ChestLootRoller(cfg).roll(1, rng));
    }

    @Test
    void rollUsesBandSpecificChance(@TempDir Path tmp) throws Exception {
        Nat20ChestLootConfig cfg = cfgWith(tmp, 0.0, 1.0, 0.0, 0.0);
        Random rng = new Random(1234L);
        assertFalse(new Nat20ChestLootRoller(cfg).roll(1, rng), "band 0 is 0%");
        assertTrue(new Nat20ChestLootRoller(cfg).roll(11, rng), "band 1 is 100%");
        assertFalse(new Nat20ChestLootRoller(cfg).roll(21, rng), "band 2 is 0%");
    }
}
