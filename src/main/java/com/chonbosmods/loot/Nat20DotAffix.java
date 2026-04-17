package com.chonbosmods.loot;

import java.util.Random;
import java.util.Set;

/**
 * Identifies DOT (damage-over-time) affix ids and rolls their authored-at-craft
 * duration. Durations are sampled uniformly in {@code [MIN_DURATION, MAX_DURATION]}
 * when the item/mob is rolled, then stored on {@link RolledAffix#duration()}.
 *
 * <p>Per the design locked 2026-04-17: shorter rolled durations are more
 * valuable because total damage over the DOT is constant and per-tick scales
 * up as duration shrinks. See {@code Nat20ElementalDotSystem} / {@code
 * Nat20DeepWoundsSystem} for the damage formula.
 */
public final class Nat20DotAffix {

    public static final float MIN_DURATION = 5.0f;
    public static final float MAX_DURATION = 15.0f;

    private static final Set<String> DOT_AFFIX_IDS = Set.of(
            "nat20:ignite",
            "nat20:cold",
            "nat20:infect",
            "nat20:corrupt",
            "nat20:deep_wounds"
    );

    private Nat20DotAffix() {}

    public static boolean isDotAffix(String affixId) {
        return affixId != null && DOT_AFFIX_IDS.contains(affixId);
    }

    /** Sample a DOT duration uniformly in {@code [MIN_DURATION, MAX_DURATION]}. */
    public static double rollDuration(Random random) {
        return MIN_DURATION + random.nextDouble() * (MAX_DURATION - MIN_DURATION);
    }
}
