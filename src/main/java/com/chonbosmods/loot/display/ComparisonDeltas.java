package com.chonbosmods.loot.display;

import javax.annotation.Nullable;
import java.util.Map;

public record ComparisonDeltas(
    Map<String, Delta> deltaByStatName
) {
    public record Delta(String symbol, String color) {}

    @Nullable
    public Delta getForStat(String statName) {
        return deltaByStatName.get(statName);
    }
}
