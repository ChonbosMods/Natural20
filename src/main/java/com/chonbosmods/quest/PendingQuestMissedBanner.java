package com.chonbosmods.quest;

import java.util.Objects;

/**
 * Persisted form of a Quest-Missed banner queued while the recipient was offline.
 * Denormalizes `topicHeader` so the banner can fire correctly even if the
 * originating QuestInstance has since left the party-quest store (ghost case
 * per design §5). Fires on the next PlayerReadyEvent via Task 8's drain hook.
 */
public record PendingQuestMissedBanner(
        String questId,
        String topicHeader,
        long queuedAtEpochMs) {
    public PendingQuestMissedBanner {
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(topicHeader, "topicHeader");
    }
}
