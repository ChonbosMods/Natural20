package com.chonbosmods.loot.display;

import javax.annotation.Nullable;

public record SocketLine(
    int index,
    boolean filled,
    @Nullable String gemName,
    @Nullable String purity,
    @Nullable String gemColor,
    @Nullable String bonusValue,
    @Nullable String bonusStat
) {}
