package com.chonbosmods.quest.model;

import com.chonbosmods.quest.ObjectiveType;

import java.util.List;
import java.util.Map;

public record QuestVariant(
    String id,
    Map<String, String> bindings,
    DialogueChunks dialogueChunks,
    List<PlayerResponse> playerResponses,
    List<ObjectiveType> objectivePool,
    Map<ObjectiveType, ObjectiveConfig> objectiveConfig
) {}
