package com.chonbosmods.loot.def;

import com.chonbosmods.loot.AffixType;
import com.chonbosmods.loot.NamePosition;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public record Nat20AffixDef(
    String id,
    AffixType type,
    String displayName,
    NamePosition namePosition,
    Set<String> categories,
    @Nullable StatScaling statScaling,
    @Nullable StatScaling procScaling,
    String targetStat,
    String modifierType,
    Map<String, AffixValueRange> valuesPerRarity,
    boolean ilvlScalable,
    @Nullable String description,
    @Nullable String cooldown,
    @Nullable String procChance,
    @Nullable Set<String> exclusiveWith,
    int frequency,
    boolean mobEligible
) {
    public AffixValueRange getValuesForRarity(String rarityId) {
        return valuesPerRarity.get(rarityId);
    }
}
