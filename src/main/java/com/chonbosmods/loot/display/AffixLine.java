package com.chonbosmods.loot.display;

import javax.annotation.Nullable;

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
    @Nullable String procChance
) {}
