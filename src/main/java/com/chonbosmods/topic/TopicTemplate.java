package com.chonbosmods.topic;

import com.chonbosmods.stats.Skill;
import javax.annotation.Nullable;
import java.util.List;

public record TopicTemplate(
    String id,
    TopicCategory category,
    String labelPattern,
    boolean subjectRequired,
    List<Perspective> perspectives,
    List<Perspective> questHookPerspectives,
    @Nullable SkillCheckDef skillCheckDef
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

    public record SkillCheckDef(Skill skill) {}
}
