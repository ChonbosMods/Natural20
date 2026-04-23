package com.chonbosmods.quest;

public record PendingQuestMissedBanner(
        String questId,
        String topicHeader,
        long queuedAtEpochMs) {}
