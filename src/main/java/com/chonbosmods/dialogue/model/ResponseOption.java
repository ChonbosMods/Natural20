package com.chonbosmods.dialogue.model;

import javax.annotation.Nullable;
import java.util.List;

public record ResponseOption(
    String id,
    String displayText,
    @Nullable String logText,
    String targetNodeId,
    ResponseMode mode,
    @Nullable DialogueCondition condition,
    @Nullable String skillCheckRef,
    @Nullable String statPrefix,
    @Nullable List<String> linkedResponses,
    ResponseType responseType
) {
    public ResponseOption {
        if (mode == null) mode = ResponseMode.DECISIVE;
        if (responseType == null) responseType = ResponseType.AUTHORED;
    }

    /** Convenience constructor for existing call sites (defaults to AUTHORED). */
    public ResponseOption(
            String id, String displayText, @Nullable String logText, String targetNodeId,
            ResponseMode mode, @Nullable DialogueCondition condition,
            @Nullable String skillCheckRef, @Nullable String statPrefix,
            @Nullable List<String> linkedResponses) {
        this(id, displayText, logText, targetNodeId, mode, condition,
             skillCheckRef, statPrefix, linkedResponses, ResponseType.AUTHORED);
    }
}
