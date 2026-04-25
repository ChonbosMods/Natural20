package com.chonbosmods.loot.mob;

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
}
