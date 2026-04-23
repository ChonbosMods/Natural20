package com.chonbosmods.quest;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.Objects;

/**
 * Persisted form of a Quest-Missed banner queued while the recipient was offline.
 * Denormalizes `topicHeader` so the banner can fire correctly even if the
 * originating QuestInstance has since left the party-quest store (ghost case
 * per design section 5). Fires on the next PlayerReadyEvent via Task 8's drain hook.
 */
public record PendingQuestMissedBanner(
        String questId,
        String topicHeader,
        long queuedAtEpochMs) {
    public PendingQuestMissedBanner {
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(topicHeader, "topicHeader");
    }

    /**
     * Mutable companion used by {@link BuilderCodec}: records lack a no-arg
     * constructor and setters, so codec round-trips go through this holder and
     * are converted to/from the immutable {@link PendingQuestMissedBanner} via
     * {@link #toRecord()} / {@link #fromRecord(PendingQuestMissedBanner)}.
     */
    public static final class Builder {
        public static final BuilderCodec<Builder> CODEC = BuilderCodec
                .builder(Builder.class, Builder::new)
                .addField(new KeyedCodec<>("QuestId", Codec.STRING),
                        Builder::setQuestId, Builder::getQuestId)
                .addField(new KeyedCodec<>("TopicHeader", Codec.STRING),
                        Builder::setTopicHeader, Builder::getTopicHeader)
                .addField(new KeyedCodec<>("QueuedAtEpochMs", Codec.LONG),
                        Builder::setQueuedAtEpochMs, Builder::getQueuedAtEpochMs)
                .build();

        private String questId = "";
        private String topicHeader = "";
        private long queuedAtEpochMs = 0L;

        public Builder() {}

        public String getQuestId() { return questId; }
        public void setQuestId(String questId) { this.questId = questId != null ? questId : ""; }

        public String getTopicHeader() { return topicHeader; }
        public void setTopicHeader(String topicHeader) { this.topicHeader = topicHeader != null ? topicHeader : ""; }

        public long getQueuedAtEpochMs() { return queuedAtEpochMs; }
        public void setQueuedAtEpochMs(long queuedAtEpochMs) { this.queuedAtEpochMs = queuedAtEpochMs; }

        public PendingQuestMissedBanner toRecord() {
            return new PendingQuestMissedBanner(questId, topicHeader, queuedAtEpochMs);
        }

        public static Builder fromRecord(PendingQuestMissedBanner b) {
            Builder out = new Builder();
            out.questId = b.questId();
            out.topicHeader = b.topicHeader();
            out.queuedAtEpochMs = b.queuedAtEpochMs();
            return out;
        }
    }
}
