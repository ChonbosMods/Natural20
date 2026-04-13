package com.chonbosmods.combat;

/**
 * Diminishing returns: softcap(v, k) = v / (1 + v/k).
 * As v grows, effective value approaches k asymptotically.
 * Each affix type defines its own k value.
 */
public final class Nat20Softcap {

    private Nat20Softcap() {}

    /**
     * Apply softcap diminishing returns.
     *
     * @param value raw effective value (must be positive for meaningful result)
     * @param k     softcap knee: higher k = more generous scaling
     * @return softcapped value, or 0 if inputs are non-positive
     */
    public static double softcap(double value, double k) {
        if (k <= 0 || value <= 0) return 0;
        return value / (1.0 + value / k);
    }
}
