package com.chonbosmods.loot.mob;

import com.chonbosmods.loot.filter.Nat20GearFilter;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Nat20MobLootPoolTest {

    private static Map<String, List<String>> buckets() {
        return Map.of(
            "melee_weapon",  List.of("mw_a", "mw_b"),
            "armor",         List.of("arm_a", "arm_b"),
            "ranged_weapon", List.of("rw_a"),
            "tool",          List.of("t_a"));
    }

    @Test
    void nativeBiasIs5Percent() {
        Nat20MobLootPool pool = Nat20MobLootPool.forTesting(buckets(), List.of("native_only"));
        Random rng = new Random(11L);
        int nativeCount = 0;
        int N = 100_000;
        for (int i = 0; i < N; i++) {
            Nat20MobLootPool.PickResult r = pool.pick(rng);
            if (r.source() == Nat20MobLootPool.Source.NATIVE) nativeCount++;
        }
        assertEquals(0.05, nativeCount / (double) N, 0.01);
    }

    @Test
    void emptyNativeFallsThroughToGlobal() {
        Nat20MobLootPool pool = Nat20MobLootPool.forTesting(buckets(), List.of());
        Random rng = new Random(3L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(Nat20MobLootPool.Source.GLOBAL, pool.pick(rng).source());
        }
    }

    @Test
    void globalDistributionIs30_30_20_20() {
        Nat20MobLootPool pool = Nat20MobLootPool.forTesting(buckets(), List.of());
        Random rng = new Random(2L);
        Map<String, Integer> counts = new HashMap<>();
        int N = 100_000;
        for (int i = 0; i < N; i++) {
            String id = pool.pick(rng).itemId();
            String cat = id.startsWith("mw") ? "melee_weapon"
                       : id.startsWith("arm") ? "armor"
                       : id.startsWith("rw") ? "ranged_weapon" : "tool";
            counts.merge(cat, 1, Integer::sum);
        }
        assertEquals(0.30, counts.get("melee_weapon")  / (double) N, 0.02);
        assertEquals(0.30, counts.get("armor")         / (double) N, 0.02);
        assertEquals(0.20, counts.get("ranged_weapon") / (double) N, 0.02);
        assertEquals(0.20, counts.get("tool")          / (double) N, 0.02);
    }

    @Test
    void buildGlobalBucketsHonorsAllowlistCategoryForModItems() {
        // This test verifies that mod-namespaced items registered via the gear filter's
        // allowlist (with explicit category) make it into the right bucket even though
        // their prefix wouldn't match Nat20ItemTierResolver.inferCategory.
        //
        // Uses the test-resource gear_filter_test.json fixture (already shipped in Task 3).
        // That fixture has Mod:Custom_Plasma allowlisted as ranged_weapon at ilvl [22, 38].

        // Set up the static filter facade to use the test fixture.
        Nat20GearFilter filter = Nat20GearFilter.loadFrom(
            getClass().getResourceAsStream("/loot/gear_filter_test.json"));
        Nat20ItemTierResolver.setFilter(filter);

        try {
            // Build a fake registry that returns only the mod itemId.
            Nat20LootEntryRegistry registry = new Nat20LootEntryRegistry() {
                @Override public Set<String> getAllItemIds() {
                    return Set.of("Mod:Custom_Plasma");
                }
            };

            Map<String, List<String>> buckets = Nat20MobLootPool.buildGlobalBuckets(registry, 30);
            assertEquals(List.of("Mod:Custom_Plasma"), buckets.get("ranged_weapon"));
            assertTrue(buckets.get("melee_weapon").isEmpty());
            assertTrue(buckets.get("armor").isEmpty());
            assertTrue(buckets.get("tool").isEmpty());
        } finally {
            // Clear the static filter so it doesn't pollute later tests.
            Nat20ItemTierResolver.setFilter(null);
        }
    }
}
