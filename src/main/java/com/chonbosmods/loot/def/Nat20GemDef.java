package com.chonbosmods.loot.def;

import com.chonbosmods.stats.Stat;

import javax.annotation.Nullable;
import java.util.Map;

public record Nat20GemDef(
    String id,
    String displayName,
    @Nullable Stat statAffinity,
    double affinityScalingFactor,
    Map<String, Double> purityMultipliers,
    Map<String, GemBonus> bonusesBySlot
) {
    public double getPurityMultiplier(String purityKey) {
        return purityMultipliers.getOrDefault(purityKey, 1.0);
    }

    @Nullable
    public GemBonus getBonusForCategory(String category) {
        return bonusesBySlot.get(category);
    }
}
