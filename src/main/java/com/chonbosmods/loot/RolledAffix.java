package com.chonbosmods.loot;

import java.util.Random;

/**
 * A rolled affix on an item. Stores a {@code [minLevel, maxLevel]} coefficient
 * range so that flat-damage affixes can roll per-hit uniformly within the
 * range (D2 behavior), while fixed-percentage affixes collapse to a single
 * value ({@code minLevel == maxLevel}).
 *
 * <p>For DOT affixes (ignite, cold, infect, corrupt, deep_wounds) the
 * {@code duration} field carries the rolled seconds-of-DOT at craft time.
 * A value of 0 means "no duration stored" — used for every non-DOT affix.
 */
public record RolledAffix(String id, double minLevel, double maxLevel, double duration) {

    public RolledAffix {
        if (minLevel > maxLevel) {
            double tmp = minLevel;
            minLevel = maxLevel;
            maxLevel = tmp;
        }
        if (duration < 0) duration = 0;
    }

    public RolledAffix(String id, double minLevel, double maxLevel) {
        this(id, minLevel, maxLevel, 0.0);
    }

    /** Convenience constructor for a fixed (no-range) roll — used by deterministic test fixtures. */
    public RolledAffix(String id, double level) {
        this(id, level, level, 0.0);
    }

    /** Midpoint level — use for fixed (percentage/score) affixes. */
    public double midLevel() {
        return (minLevel + maxLevel) * 0.5;
    }

    /** Uniformly-sampled level within {@code [minLevel, maxLevel]} — use for per-hit flat damage rolls. */
    public double rollLevel(Random random) {
        if (minLevel == maxLevel) return minLevel;
        return minLevel + random.nextDouble() * (maxLevel - minLevel);
    }

    /** True when the roll collapsed to a single value (display as "6" not "6-6"). */
    public boolean isFixed() {
        return minLevel == maxLevel;
    }

    /** True when this affix carries a rolled DOT duration. */
    public boolean hasDuration() {
        return duration > 0;
    }
}
