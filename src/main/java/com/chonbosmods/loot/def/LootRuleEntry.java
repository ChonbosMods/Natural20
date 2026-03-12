package com.chonbosmods.loot.def;

import com.chonbosmods.loot.AffixType;

public record LootRuleEntry(
    AffixType type,
    int count,
    double probability
) {}
