package com.chonbosmods.topic;

import java.util.*;

/**
 * Cross-settlement dedup using percentage-based windows.
 * When 80% of a pool has been seen, the seen set clears and the full pool
 * refreshes. This ensures the player sees most of the pool before any repeat.
 *
 * <p>Instances are intended to be shared across all settlements within a
 * single world (see {@link TopicGenerator}). State is persisted between
 * sessions via {@link #snapshot()} / {@link #restore(Map)}.
 *
 * <p>Not thread-safe. World-gen is processed on the world thread, so a single
 * instance per world is sufficient.
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

    /**
     * Capture the seen-entry state as a Gson-friendly map for persistence.
     * Returned map is a defensive copy: callers can serialize without
     * affecting live state, and mutating the snapshot does not feed back.
     */
    public Map<String, List<Integer>> snapshot() {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        for (var entry : seenEntries.entrySet()) {
            // Sort for stable serialization output (helps diffs / tests).
            List<Integer> ordered = new ArrayList<>(entry.getValue());
            Collections.sort(ordered);
            out.put(entry.getKey(), ordered);
        }
        return out;
    }

    /**
     * Replace internal state from a previously captured snapshot.
     * Tolerates null entries (treats as empty).
     */
    public void restore(Map<String, List<Integer>> snapshot) {
        seenEntries.clear();
        if (snapshot == null) return;
        for (var entry : snapshot.entrySet()) {
            if (entry.getValue() == null) continue;
            seenEntries.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }
}
