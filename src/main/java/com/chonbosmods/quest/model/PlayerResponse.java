package com.chonbosmods.quest.model;

import javax.annotation.Nullable;

public record PlayerResponse(
    String text,
    String action,
    @Nullable Integer dispositionShift
) {}
