package com.chonbosmods.dialogue.model;

import javax.annotation.Nullable;

public record ActiveFollowUp(
    String responseId,
    String displayText,
    @Nullable String logText,
    @Nullable String statPrefix,
    boolean grayed
) {}
