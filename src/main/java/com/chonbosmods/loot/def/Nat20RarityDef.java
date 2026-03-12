package com.chonbosmods.loot.def;

import java.util.List;

public record Nat20RarityDef(
    String id,
    int qualityValue,
    String color,
    String displayName,
    int baseWeight,
    int maxAffixes,
    int maxSockets,
    int statRequirement,
    String tooltipTexture,
    String tooltipArrowTexture,
    String slotTexture,
    List<LootRuleEntry> lootRules
) {}
