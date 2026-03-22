package com.chonbosmods.dialogue.model;

import javax.annotation.Nullable;

public sealed interface LogEntry {

    record TopicHeader(String label, boolean questTopic) implements LogEntry {}

    record NpcSpeech(String text) implements LogEntry {}

    record SelectedResponse(
        String responseId,
        String displayText,
        @Nullable String statPrefix
    ) implements LogEntry {}

    record SystemText(String text) implements LogEntry {}

    record ReturnGreeting(String text) implements LogEntry {}

    record ReturnDivider() implements LogEntry {}
}
