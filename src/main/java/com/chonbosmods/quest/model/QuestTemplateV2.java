package com.chonbosmods.quest.model;

import java.util.List;
import java.util.Map;

/**
 * Complete v2 quest template: one template = one full quest narrative.
 * Loaded from quests/v2/*.json.
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
    List<ObjectiveConfig> objectives,
    Map<String, Double> npcWeights
) {}
