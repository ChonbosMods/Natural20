package com.chonbosmods.dialogue;

import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.model.ExhaustionState;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ConditionContext(
    UUID playerId,
    String npcId,
    Nat20PlayerData playerData,
    int disposition,
    Map<String, ExhaustionState> exhaustedTopics,
    Set<String> learnedGlobalTopics
) {}
