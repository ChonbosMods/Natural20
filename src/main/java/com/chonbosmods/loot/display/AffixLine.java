package com.chonbosmods.loot.display;

import javax.annotation.Nullable;

/**
 * Resolved data for a single affix tooltip line.
 *
 * <p>The {@code renderedText} field is the pre-formatted display string with Hytale color markup
 * ({@code <color is="#hex">...</color>}) already applied. Display-oriented builders should emit
 * it directly. The remaining numeric fields ({@code value}, {@code unit}, {@code statName}) are
 * retained so {@link ComparisonDeltas} can diff STAT-type affixes between items.
 */
public record AffixLine(
    String name,
    String value,
    String unit,
    String statName,
    @Nullable String scalingStat,
    String type,
    boolean requirementMet,
    @Nullable String requirementText,
    @Nullable String description,
    @Nullable String cooldown,
    @Nullable String procChance,
    String renderedText
) {}
