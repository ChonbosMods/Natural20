package com.chonbosmods.loot.def;

import com.chonbosmods.loot.AffixType;
import com.chonbosmods.loot.NamePosition;
import com.chonbosmods.stats.Stat;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public record Nat20AffixDef(
    String id,
    AffixType type,
    String displayName,
    NamePosition namePosition,
    Set<String> categories,
    @Nullable Map<Stat, Integer> statRequirement,
    @Nullable StatScaling statScaling,
    String targetStat,
    String modifierType,
    Map<String, AffixValueRange> valuesPerRarity,
    @Nullable String description,
    @Nullable String cooldown,
    @Nullable String procChance,
    @Nullable Set<String> exclusiveWith,
    int frequency
) {
    public AffixValueRange getValuesForRarity(String rarityId) {
        return valuesPerRarity.get(rarityId);
    }
}
