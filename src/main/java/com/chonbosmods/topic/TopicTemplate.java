package com.chonbosmods.topic;

import javax.annotation.Nullable;
import java.util.List;

public record TopicTemplate(
    String id,
    String labelPattern,
    boolean subjectRequired,
    boolean requiresConcrete,
    @Nullable List<String> skills,
    @Nullable String reactionIntensity,
    @Nullable List<String> detailPrompts,
    @Nullable List<String> reactionPrompts,
    @Nullable String introPattern,
    @Nullable Decisive decisive
) {
    public record Decisive(
        String prompt,
        String response
    ) {}
}
