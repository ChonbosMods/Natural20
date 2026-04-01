package com.chonbosmods.topic;

import java.util.*;

/**
 * Settlement-wide dedup using percentage-based windows.
 * When 80% of a pool has been seen, the seen set clears and the full pool
 * refreshes. This ensures the player sees most of the pool before any repeat.
 */
public class PercentageDedup {

    private static final double DEDUP_PERCENTAGE = 0.80;

    private final Map<String, Set<Integer>> seenEntries = new HashMap<>();

    public int draw(String poolName, int poolSize, Random random) {
        if (poolSize <= 0) return 0;

        Set<Integer> seen = seenEntries.computeIfAbsent(poolName, k -> new HashSet<>());

        int threshold = (int) Math.ceil(poolSize * DEDUP_PERCENTAGE);
        if (seen.size() >= threshold) {
            seen.clear();
        }

        int selected;
        int attempts = 0;
        do {
            selected = random.nextInt(poolSize);
            attempts++;
        } while (seen.contains(selected) && attempts < poolSize);

        seen.add(selected);
        return selected;
    }

    public String drawFrom(String poolName, List<String> pool, Random random) {
        if (pool.isEmpty()) return "";
        int idx = draw(poolName, pool.size(), random);
        return pool.get(idx);
    }

    public void clear() {
        seenEntries.clear();
    }
}
