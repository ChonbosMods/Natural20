package com.chonbosmods.dialogue.model;

import javax.annotation.Nullable;

public record ActiveFollowUp(
    String responseId,
    String displayText,
    @Nullable String logText,
    @Nullable String statPrefix,
    boolean grayed,
    ResponseType responseType
) {
    /** Convenience constructor for existing call sites (defaults to AUTHORED). */
    public ActiveFollowUp(String responseId, String displayText,
                           @Nullable String logText, @Nullable String statPrefix, boolean grayed) {
        this(responseId, displayText, logText, statPrefix, grayed, ResponseType.AUTHORED);
    }
}
