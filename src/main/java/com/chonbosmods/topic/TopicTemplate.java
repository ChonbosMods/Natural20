package com.chonbosmods.topic;

import javax.annotation.Nullable;
import java.util.List;

public record TopicTemplate(
    String id,
    TopicCategory category,
    String labelTemplate,
    List<Perspective> perspectives,
    List<Perspective> questHookPerspectives
) {
    public record Perspective(
        String intro,
        List<FollowUp> exploratories,
        @Nullable FollowUp decisive
    ) {}

    public record FollowUp(
        String prompt,
        String response,
        List<FollowUp> exploratories
    ) {}
}
