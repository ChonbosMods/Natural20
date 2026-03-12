package com.chonbosmods.loot;

import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nullable;
import java.util.List;

public record Nat20ItemDisplayData(
    Message name,
    Message rarityLabel,
    String rarityColor,
    List<Message> affixLines,
    List<Message> socketLines,
    @Nullable Message requirementLine,
    String slotTexture,
    String tooltipTexture
) {}
