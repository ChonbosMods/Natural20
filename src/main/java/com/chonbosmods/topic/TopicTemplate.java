package com.chonbosmods.topic;

import com.chonbosmods.stats.Skill;
import javax.annotation.Nullable;
import java.util.List;

public record TopicTemplate(
    String id,
    @Nullable TopicCategory category,
    String labelPattern,
    boolean subjectRequired,
    boolean requiresConcrete,
    List<Perspective> perspectives,
    List<Perspective> questHookPerspectives,
    @Nullable SkillCheckDef skillCheckDef,
    // v2 fields
    @Nullable List<String> skills,
    @Nullable String reactionIntensity,
    @Nullable List<String> detailPrompts,
    @Nullable List<String> reactionPrompts,
    @Nullable String introPattern,
    @Nullable Decisive decisive
) {
    /** Backward-compat factory for old-format (8-arg) callers. */
    public static TopicTemplate ofLegacy(String id, TopicCategory category, String labelPattern,
                                          boolean subjectRequired, boolean requiresConcrete,
                                          List<Perspective> perspectives, List<Perspective> questHookPerspectives,
                                          @Nullable SkillCheckDef skillCheckDef) {
        return new TopicTemplate(id, category, labelPattern, subjectRequired, requiresConcrete,
            perspectives, questHookPerspectives, skillCheckDef,
            null, null, null, null, null, null);
    }

    /** True if this template uses the v2 coherent-pool format. */
    public boolean isV2() {
        return skills != null;
    }

    public record Decisive(
        String prompt,
        String response
    ) {}
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
