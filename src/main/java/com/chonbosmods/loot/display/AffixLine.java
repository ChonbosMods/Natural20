package com.chonbosmods.loot.display;

import javax.annotation.Nullable;

/**
 * Resolved data for a single affix tooltip line. {@code renderedText} is the pre-formatted
 * display string with Hytale color markup already applied; numeric fields remain for debug
 * inspection via {@code /nat20 lootinspect}.
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
