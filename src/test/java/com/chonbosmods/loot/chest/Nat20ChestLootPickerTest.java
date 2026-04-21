package com.chonbosmods.loot.chest;

import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for {@link Nat20ChestLootPicker} is limited: the real pipeline
 * depends on a multi-registry graph ({@code Nat20RarityRegistry},
 * {@code Nat20AffixRegistry}, {@code Nat20ItemRegistry} with reflection into
 * Hytale's {@code Item} asset map, and {@code Nat20NamePoolRegistry}) that
 * fights in-process instantiation. The project does not use Mockito.
 *
 * <p>This test asserts only the "empty pool" branch, which exercises the
 * picker without reaching the pipeline. Full behaviour (pipeline integration,
 * category inference, display-name resolution, rarity gating) is covered by
 * the Task 8 in-world smoke test.
 */
class Nat20ChestLootPickerTest {

    @Test
    void pickLootReturnsEmptyWhenPoolIsEmpty() {
        Nat20LootSystem lootSystem = new Nat20LootSystem();
        // No loadAll(...) call: the loot entry registry is empty, so the
        // filtered gear pool is also empty and pickLoot short-circuits.
        Nat20ChestLootPicker picker = new Nat20ChestLootPicker(lootSystem);

        Optional<Nat20LootData> result = picker.pickLoot(15, new Random(1234L));

        assertTrue(result.isEmpty(), "empty registry should produce no loot");
    }
}
