package com.chonbosmods.loot;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CategoryWeightedPickerTest {

    private static Map<String, List<String>> fullBuckets() {
        return Map.of(
            "melee_weapon",  List.of("mw_a", "mw_b"),
            "armor",         List.of("arm_a", "arm_b"),
            "ranged_weapon", List.of("rw_a"),
            "tool",          List.of("t_a"));
    }

    @Test
    void deterministicWithSeededRng() {
        var b = fullBuckets();
        Random a = new Random(42L), b2 = new Random(42L);
        assertEquals(CategoryWeightedPicker.pick(b, a), CategoryWeightedPicker.pick(b, b2));
    }

    @Test
    void distributionRoughlyMatchesWeights() {
        var b = fullBuckets();
        Random rng = new Random(1L);
        Map<String, Integer> counts = new HashMap<>();
        int N = 100_000;
        for (int i = 0; i < N; i++) {
            String pick = CategoryWeightedPicker.pick(b, rng);
            String cat = pick.startsWith("mw") ? "melee_weapon"
                       : pick.startsWith("arm") ? "armor"
                       : pick.startsWith("rw") ? "ranged_weapon" : "tool";
            counts.merge(cat, 1, Integer::sum);
        }
        // Allow ±2% slack at 100k samples
        assertEquals(0.30, counts.get("melee_weapon")  / (double) N, 0.02);
        assertEquals(0.30, counts.get("armor")         / (double) N, 0.02);
        assertEquals(0.20, counts.get("ranged_weapon") / (double) N, 0.02);
        assertEquals(0.20, counts.get("tool")          / (double) N, 0.02);
    }

    @Test
    void renormalizesWhenBucketEmpty() {
        Map<String, List<String>> b = Map.of(
            "melee_weapon",  List.of(),
            "armor",         List.of("arm_a"),
            "ranged_weapon", List.of("rw_a"),
            "tool",          List.of("t_a"));
        Random rng = new Random(7L);
        for (int i = 0; i < 1000; i++) {
            String pick = CategoryWeightedPicker.pick(b, rng);
            assertFalse(pick.startsWith("mw"), "must never return melee item");
        }
    }

    @Test
    void returnsNullWhenAllBucketsEmpty() {
        Map<String, List<String>> b = Map.of(
            "melee_weapon",  List.of(),
            "armor",         List.of(),
            "ranged_weapon", List.of(),
            "tool",          List.of());
        assertNull(CategoryWeightedPicker.pick(b, new Random(0)));
    }
}
