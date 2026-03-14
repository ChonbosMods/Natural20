package com.chonbosmods.loot.display;

import com.chonbosmods.loot.Nat20ItemDisplayData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public record ComparisonDeltas(
    Map<String, Delta> deltaByStatName
) {
    public record Delta(String symbol, String color) {}

    private static final String COLOR_BETTER = "#33cc33";
    private static final String COLOR_WORSE = "#cc3333";

    @Nullable
    public Delta getForStat(String statName) {
        return deltaByStatName.get(statName);
    }

    /**
     * Compute comparison deltas between a hovered item and an equipped item.
     * Only STAT-type affixes are compared. EFFECT affixes are skipped.
     *
     * @param hovered  the item being hovered
     * @param equipped the currently equipped item
     * @return deltas keyed by stat name, or null if no meaningful comparison exists
     */
    @Nullable
    public static ComparisonDeltas compute(Nat20ItemDisplayData hovered, Nat20ItemDisplayData equipped) {
        Map<String, Double> hoveredStats = collectStatValues(hovered);
        Map<String, Double> equippedStats = collectStatValues(equipped);

        if (hoveredStats.isEmpty() && equippedStats.isEmpty()) return null;

        Map<String, Delta> deltas = new HashMap<>();

        // Stats on the hovered item
        for (var entry : hoveredStats.entrySet()) {
            String stat = entry.getKey();
            double hoveredVal = entry.getValue();
            double equippedVal = equippedStats.getOrDefault(stat, 0.0);
            double diff = hoveredVal - equippedVal;
            if (diff > 0.01) {
                deltas.put(stat, new Delta("+" + formatDelta(diff), COLOR_BETTER));
            } else if (diff < -0.01) {
                deltas.put(stat, new Delta(formatDelta(diff), COLOR_WORSE));
            }
        }

        // Stats only on the equipped item (losses)
        for (var entry : equippedStats.entrySet()) {
            if (hoveredStats.containsKey(entry.getKey())) continue;
            double diff = -entry.getValue();
            deltas.put(entry.getKey(), new Delta(formatDelta(diff), COLOR_WORSE));
        }

        return deltas.isEmpty() ? null : new ComparisonDeltas(deltas);
    }

    private static Map<String, Double> collectStatValues(Nat20ItemDisplayData data) {
        Map<String, Double> stats = new HashMap<>();
        for (AffixLine affix : data.affixes()) {
            if (!"STAT".equals(affix.type())) continue;
            try {
                double val = Double.parseDouble(affix.value().replace("+", ""));
                if ("%".equals(affix.unit())) {
                    val = val / 100.0;
                }
                stats.merge(affix.statName(), val, Double::sum);
            } catch (NumberFormatException e) {
                // Skip unparseable values
            }
        }
        return stats;
    }

    private static String formatDelta(double delta) {
        if (delta == (int) delta) {
            return String.format("%+d", (int) delta);
        }
        return String.format("%+.1f", delta);
    }
}
