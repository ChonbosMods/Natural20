package com.chonbosmods.dialogue.model;

import javax.annotation.Nullable;
import java.util.List;

public record ResponseOption(
    String id,
    String displayText,
    String targetNodeId,
    ResponseMode mode,
    @Nullable DialogueCondition condition,
    @Nullable String skillCheckRef,
    @Nullable String statPrefix,
    @Nullable List<String> linkedResponses
) {
    public ResponseOption {
        if (mode == null) mode = ResponseMode.DECISIVE;
    }
}
