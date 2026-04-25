package com.chonbosmods.loot;

import java.util.*;

public final class CategoryWeightedPicker {

    public static final Map<String, Integer> WEIGHTS = Map.of(
            "melee_weapon",  30,
            "armor",         30,
            "ranged_weapon", 20,
            "tool",          20);

    private CategoryWeightedPicker() {}

    public static String pick(Map<String, List<String>> buckets, Random rng) {
        Map<String, Integer> active = new HashMap<>();
        int total = 0;
        for (var e : WEIGHTS.entrySet()) {
            List<String> bucket = buckets.get(e.getKey());
            if (bucket != null && !bucket.isEmpty()) {
                active.put(e.getKey(), e.getValue());
                total += e.getValue();
            }
        }
        if (total == 0) return null;

        int roll = rng.nextInt(total);
        String chosen = null;
        for (var e : active.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) { chosen = e.getKey(); break; }
        }
        List<String> bucket = buckets.get(chosen);
        return bucket.get(rng.nextInt(bucket.size()));
    }
}
