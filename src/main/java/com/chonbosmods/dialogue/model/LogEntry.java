package com.chonbosmods.dialogue.model;

import javax.annotation.Nullable;

public sealed interface LogEntry {

    record TopicHeader(String label) implements LogEntry {}

    record NpcSpeech(String text) implements LogEntry {}

    record FollowUp(
        String responseId,
        String displayText,
        @Nullable String statPrefix,
        FollowUpState state
    ) implements LogEntry {
        public FollowUp withState(FollowUpState newState) {
            return new FollowUp(responseId, displayText, statPrefix, newState);
        }
    }

    record SystemText(String text) implements LogEntry {}

    record ReturnGreeting(String text) implements LogEntry {}

    record ReturnDivider() implements LogEntry {}
}
