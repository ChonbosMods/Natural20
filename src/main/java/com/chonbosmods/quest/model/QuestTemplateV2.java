package com.chonbosmods.quest.model;

import com.chonbosmods.stats.Skill;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Complete v2 quest template: one template = one full quest narrative.
 * Loaded from {@code quests/v2/index.json}.
 *
 * <p>Each template is a designed arc. The number of objectives in {@link #objectives}
 * dictates the conflict count at runtime: a 2-objective template has 1 conflict, a
 * 5-objective template has 4 conflicts. The roll-vs-cap conflict-count model from v1
 * is gone — selection is template-driven.
 *
 * <p>Rewards are difficulty-driven at runtime: the reward item is rolled by
 * {@code AffixRewardRoller} against the quest's rolled {@code DifficultyConfig},
 * and its display name binds to {@code {reward_item}}. XP comes from the
 * difficulty config, not the template.
 *
 * <p>{@code roleAffinity} is a hard eligibility filter. An empty or null list means
 * the template is eligible for any quest-bearer role. A non-empty list restricts
 * selection to NPCs whose role matches one of the listed roles.
 *
 * <p>{@code skillCheck} is optional. When present, the dialogue manager surfaces a
 * stat-check option in the accept/decline dialog. The pass branch reveals the
 * NPC's deeper layer ({@code passText}) and stamps {@code skillcheckPassed} on the
 * resulting {@code QuestInstance}, which {@code TURN_IN_V2} reads to apply the
 * skillcheck pass reward bonus.
 */
public record QuestTemplateV2(
    String id,
    String topicHeader,
    String situation,
    List<ObjectiveConfig> objectives,
    String expositionText,
    String acceptText,
    String declineText,
    String expositionTurnInText,
    String conflict1Text,
    String conflict1TurnInText,
    @Nullable String conflict2Text,
    @Nullable String conflict2TurnInText,
    @Nullable String conflict3Text,
    @Nullable String conflict3TurnInText,
    @Nullable String conflict4Text,
    @Nullable String conflict4TurnInText,
    String resolutionText,
    @Nullable SkillCheck skillCheck,
    String valence,
    @Nullable List<String> roleAffinity,
    @Nullable String targetNpcOpener,
    @Nullable String targetNpcCloser,
    @Nullable String targetNpcOpener2,
    @Nullable String targetNpcCloser2
) {
    /**
     * Optional skill check at the accept/decline phase. The skill type must be
     * coherent with the pass/fail text content (see authoring rules).
     */
    public record SkillCheck(Skill skill, int dc, String passText, String failText) {}
}
