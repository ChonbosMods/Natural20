package com.chonbosmods.loot.def;

import javax.annotation.Nullable;
import java.util.Map;

public record Nat20MobAffixDef(
    String id,
    String displayName,
    String color,
    Map<String, Double> statMultipliers,
    String abilityType,
    @Nullable Map<String, Object> abilityConfig,
    double lootBonusMultiplier,
    int minTier
) {}
