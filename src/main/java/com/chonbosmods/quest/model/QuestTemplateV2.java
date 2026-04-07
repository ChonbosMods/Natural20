package com.chonbosmods.quest.model;

import java.util.List;
import java.util.Map;

/**
 * Complete v2 quest template: one template = one full quest narrative.
 * Loaded from quests/v2/*.json.
 *
 * <p>{@code rewardText} is a free-form description of what the player receives on
 * completion (e.g. "a pouch of silver and a hot meal at the tavern"). It is bound
 * to the {@code {quest_reward}} variable so authors can reference it in any text
 * field. When omitted, the variable falls back to a generic placeholder.
 */
public record QuestTemplateV2(
    String situation,
    String topicHeader,
    String expositionText,
    String acceptText,
    String declineText,
    List<String> skillcheckTypes,
    String skillcheckPassText,
    String skillcheckFailText,
    String expositionTurnInText,
    String conflict1Text,
    String conflict1TurnInText,
    String conflict2Text,
    String conflict2TurnInText,
    String resolutionText,
    String rewardText,
    List<ObjectiveConfig> objectives,
    Map<String, Double> npcWeights
) {}
