package com.chonbosmods.dialogue.model;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public record DialogueCondition(
    @Nullable String type,
    @Nullable Map<String, String> params,
    @Nullable List<DialogueCondition> all,
    @Nullable List<DialogueCondition> any
) {
    public boolean isComposite() {
        return all != null || any != null;
    }
}
