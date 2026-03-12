package com.chonbosmods.dialogue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side state for a single dialogue conversation.
 * Maintains the ordered list of rows that map to BarterTradeRow entries in #TradeGrid.
 */
public class ConversationState {

    public enum RowType {
        DISPOSITION,     // Type A: non-clickable disposition display
        NPC_SPEECH,      // Type B: non-clickable NPC text
        TOPIC,           // Type C: clickable topic option
        FOLLOW_UP,       // Type D: clickable follow-up response
        GOODBYE          // Type E: clickable goodbye button
    }

    public record RowEntry(
            RowType type,
            String text,
            @Nullable String eventType,    // "topic", "followup", "goodbye", null for non-clickable
            @Nullable String eventId,      // topic ID or follow-up index
            boolean enabled,               // false = locked/disabled button
            @Nullable String skillCheckNodeId  // if this follow-up triggers a skill check
    ) {
        /** Non-clickable row (disposition, NPC speech) */
        public static RowEntry display(RowType type, String text) {
            return new RowEntry(type, text, null, null, true, null);
        }

        /** Clickable topic row */
        public static RowEntry topic(String text, String topicNodeId, boolean enabled) {
            return new RowEntry(RowType.TOPIC, text, "topic", topicNodeId, enabled, null);
        }

        /** Clickable follow-up row */
        public static RowEntry followUp(String text, String targetNodeId, boolean enabled,
                                         @Nullable String skillCheckNodeId) {
            return new RowEntry(RowType.FOLLOW_UP, text, "followup", targetNodeId, enabled, skillCheckNodeId);
        }

        /** Goodbye button row */
        public static RowEntry goodbye() {
            return new RowEntry(RowType.GOODBYE, "[ Goodbye ]", "goodbye", null, true, null);
        }
    }

    private final List<RowEntry> rows = new ArrayList<>();
    private final Set<String> exhaustedTopics = new HashSet<>();
    private int disposition = 50;
    private boolean followUpsPending = false;

    public List<RowEntry> getRows() {
        return rows;
    }

    public Set<String> getExhaustedTopics() {
        return exhaustedTopics;
    }

    public int getDisposition() {
        return disposition;
    }

    public void setDisposition(int disposition) {
        this.disposition = disposition;
    }

    public boolean hasFollowUpsPending() {
        return followUpsPending;
    }

    public void setFollowUpsPending(boolean pending) {
        this.followUpsPending = pending;
    }

    /** Clear and rebuild rows list. Called before each grid rebuild. */
    public void clearRows() {
        rows.clear();
    }

    public void addRow(RowEntry entry) {
        rows.add(entry);
    }

    public void exhaustTopic(String topicNodeId) {
        exhaustedTopics.add(topicNodeId);
    }

    public boolean isTopicExhausted(String topicNodeId) {
        return exhaustedTopics.contains(topicNodeId);
    }
}
