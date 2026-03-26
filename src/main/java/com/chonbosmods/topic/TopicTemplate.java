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
        @Nullable List<IntentSlot> intents,
        @Nullable String deepenerResponse,
        @Nullable FollowUp decisive
    ) {
        /** True if this perspective uses the new intent-based format. */
        public boolean usesIntents() {
            return intents != null && !intents.isEmpty();
        }
    }

    public record FollowUp(
        String prompt,
        String response,
        List<FollowUp> exploratories
    ) {}

    public record IntentSlot(
        String intent,
        String response,
        @Nullable String deepenerResponse
    ) {}

    public record SkillCheckDef(Skill skill) {}
}
