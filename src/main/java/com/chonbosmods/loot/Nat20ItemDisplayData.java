package com.chonbosmods.loot;

import com.chonbosmods.loot.display.AffixLine;
import com.chonbosmods.loot.display.RequirementLine;
import com.chonbosmods.loot.display.SocketLine;

import javax.annotation.Nullable;
import java.util.List;

public record Nat20ItemDisplayData(
    String name,
    String rarity,
    String rarityColor,
    String tooltipTexture,
    String slotTexture,
    List<AffixLine> affixes,
    List<SocketLine> sockets,
    @Nullable RequirementLine requirement,
    @Nullable String description
) {}
