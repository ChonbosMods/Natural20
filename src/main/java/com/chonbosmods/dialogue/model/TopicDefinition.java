package com.chonbosmods.dialogue.model;

import javax.annotation.Nullable;

public record TopicDefinition(
    String id,
    String label,
    String entryNodeId,
    TopicScope scope,
    @Nullable DialogueCondition condition,
    boolean startLearned,
    @Nullable String statPrefix,
    int sortOrder,
    @Nullable String recapText,
    boolean questTopic
) {}
