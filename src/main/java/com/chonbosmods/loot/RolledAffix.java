package com.chonbosmods.loot;

import java.util.Random;

/**
 * A rolled affix on an item. Stores a [minLevel, maxLevel] coefficient range so that
 * flat-damage affixes can roll per-hit uniformly within the range (D2 behavior), while
 * fixed-percentage affixes collapse to a single value (minLevel == maxLevel).
 */
public record RolledAffix(String id, double minLevel, double maxLevel) {

    public RolledAffix {
        if (minLevel > maxLevel) {
            double tmp = minLevel;
            minLevel = maxLevel;
            maxLevel = tmp;
        }
    }

    /** Convenience constructor for a fixed (no-range) roll — used by deterministic test fixtures. */
    public RolledAffix(String id, double level) {
        this(id, level, level);
    }

    /** Midpoint level — use for fixed (percentage/score) affixes. */
    public double midLevel() {
        return (minLevel + maxLevel) * 0.5;
    }

    /** Uniformly-sampled level within [minLevel, maxLevel] — use for per-hit flat damage rolls. */
    public double rollLevel(Random random) {
        if (minLevel == maxLevel) return minLevel;
        return minLevel + random.nextDouble() * (maxLevel - minLevel);
    }

    /** True when the roll collapsed to a single value (display as "6" not "6-6"). */
    public boolean isFixed() {
        return minLevel == maxLevel;
    }
}
