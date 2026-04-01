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

    record SkillCheckResult(
        String statAbbreviation,  // "WIS", "CHA", "INT", etc.
        String skillName,         // "Perception", "Persuasion", etc.
        int totalRoll,            // final roll value
        boolean passed,           // true = success
        boolean critical          // true = nat 1 or nat 20
    ) implements LogEntry {}
}
