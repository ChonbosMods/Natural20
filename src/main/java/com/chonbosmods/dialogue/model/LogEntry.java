package com.chonbosmods.dialogue.model;

import com.google.gson.JsonObject;

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
        String statAbbreviation,
        String skillName,
        int totalRoll,
        boolean passed,
        boolean critical,
        int xpGained
    ) implements LogEntry {}

    /** Serialize any LogEntry to JSON with a discriminator "type" field. */
    static JsonObject toJson(LogEntry entry) {
        var obj = new JsonObject();
        switch (entry) {
            case TopicHeader h -> {
                obj.addProperty("type", "TopicHeader");
                obj.addProperty("label", h.label());
                if (h.questTopic()) obj.addProperty("questTopic", true);
            }
            case NpcSpeech s -> {
                obj.addProperty("type", "NpcSpeech");
                obj.addProperty("text", s.text());
            }
            case SelectedResponse s -> {
                obj.addProperty("type", "SelectedResponse");
                obj.addProperty("responseId", s.responseId());
                obj.addProperty("displayText", s.displayText());
                if (s.statPrefix() != null) obj.addProperty("statPrefix", s.statPrefix());
            }
            case SystemText s -> {
                obj.addProperty("type", "SystemText");
                obj.addProperty("text", s.text());
            }
            case ReturnGreeting r -> {
                obj.addProperty("type", "ReturnGreeting");
                obj.addProperty("text", r.text());
            }
            case ReturnDivider ignored -> obj.addProperty("type", "ReturnDivider");
            case SkillCheckResult r -> {
                obj.addProperty("type", "SkillCheckResult");
                obj.addProperty("statAbbreviation", r.statAbbreviation());
                obj.addProperty("skillName", r.skillName());
                obj.addProperty("totalRoll", r.totalRoll());
                obj.addProperty("passed", r.passed());
                obj.addProperty("critical", r.critical());
                obj.addProperty("xpGained", r.xpGained());
            }
        }
        return obj;
    }

    /** Deserialize a LogEntry from JSON. Returns null for unknown types. */
    @Nullable
    static LogEntry fromJson(JsonObject obj) {
        String type = obj.get("type").getAsString();
        return switch (type) {
            case "TopicHeader" -> new TopicHeader(
                obj.get("label").getAsString(),
                obj.has("questTopic") && obj.get("questTopic").getAsBoolean());
            case "NpcSpeech" -> new NpcSpeech(obj.get("text").getAsString());
            case "SelectedResponse" -> new SelectedResponse(
                obj.get("responseId").getAsString(),
                obj.get("displayText").getAsString(),
                obj.has("statPrefix") ? obj.get("statPrefix").getAsString() : null);
            case "SystemText" -> new SystemText(obj.get("text").getAsString());
            case "ReturnGreeting" -> new ReturnGreeting(obj.get("text").getAsString());
            case "ReturnDivider" -> new ReturnDivider();
            case "SkillCheckResult" -> new SkillCheckResult(
                obj.get("statAbbreviation").getAsString(),
                obj.get("skillName").getAsString(),
                obj.get("totalRoll").getAsInt(),
                obj.get("passed").getAsBoolean(),
                obj.has("critical") && obj.get("critical").getAsBoolean(),
                obj.has("xpGained") ? obj.get("xpGained").getAsInt() : 0);
            default -> null;
        };
    }
}
