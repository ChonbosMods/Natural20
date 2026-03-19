package com.chonbosmods.topic;

import com.google.common.flogger.FluentLogger;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TopicPoolRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    public record SubjectEntry(String value, boolean plural, boolean questEligible) {}

    private final List<SubjectEntry> subjectFocuses = new ArrayList<>();
    private final List<String> greetingLines = new ArrayList<>();
    private final List<String> returnGreetingLines = new ArrayList<>();
    private final List<String> rumorDetails = new ArrayList<>();
    private final List<String> rumorSources = new ArrayList<>();
    private final List<String> smalltalkOpeners = new ArrayList<>();
    private final List<String> perspectiveDetails = new ArrayList<>();
    private final List<String> followUpPrompts = new ArrayList<>();
    private final List<String> followUpResponses = new ArrayList<>();
    private final List<String> decisivePrompts = new ArrayList<>();
    private final List<String> decisiveResponses = new ArrayList<>();

    public void loadAll(@Nullable Path poolsDir) {
        if (poolsDir == null || !Files.isDirectory(poolsDir)) {
            LOGGER.atWarning().log("Topic pools directory not found: %s", poolsDir);
            return;
        }

        loadSubjectPool(poolsDir.resolve("subject_focuses.json"));
        loadGreetingPool(poolsDir.resolve("greeting_lines.json"));
        loadStringPool(poolsDir.resolve("rumor_details.json"), rumorDetails);
        loadStringPool(poolsDir.resolve("rumor_sources.json"), rumorSources);
        loadStringPool(poolsDir.resolve("smalltalk_openers.json"), smalltalkOpeners);
        loadStringPool(poolsDir.resolve("perspective_details.json"), perspectiveDetails);
        loadStringPool(poolsDir.resolve("follow_up_prompts.json"), followUpPrompts);
        loadStringPool(poolsDir.resolve("follow_up_responses.json"), followUpResponses);
        loadStringPool(poolsDir.resolve("decisive_prompts.json"), decisivePrompts);
        loadStringPool(poolsDir.resolve("decisive_responses.json"), decisiveResponses);

        LOGGER.atInfo().log("Loaded topic pools: %d subjects, %d greetings, %d return greetings",
            subjectFocuses.size(), greetingLines.size(), returnGreetingLines.size());
    }

    private void loadSubjectPool(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("subjects")) {
                JsonObject obj = el.getAsJsonObject();
                subjectFocuses.add(new SubjectEntry(
                    obj.get("value").getAsString(),
                    obj.has("plural") && obj.get("plural").getAsBoolean(),
                    obj.has("questEligible") && obj.get("questEligible").getAsBoolean()
                ));
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load subject pool: %s", file);
        }
    }

    private void loadGreetingPool(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("greetings")) greetingLines.add(el.getAsString());
            for (JsonElement el : root.getAsJsonArray("returnGreetings")) returnGreetingLines.add(el.getAsString());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load greeting pool: %s", file);
        }
    }

    private void loadStringPool(Path file, List<String> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement el : arr) target.add(el.getAsString());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load string pool: %s", file);
        }
    }

    // --- Random selection methods ---

    public SubjectEntry randomSubject(Random random) {
        return subjectFocuses.get(random.nextInt(subjectFocuses.size()));
    }

    public SubjectEntry randomQuestEligibleSubject(Random random) {
        List<SubjectEntry> eligible = subjectFocuses.stream()
            .filter(SubjectEntry::questEligible).toList();
        if (eligible.isEmpty()) return randomSubject(random);
        return eligible.get(random.nextInt(eligible.size()));
    }

    public String randomGreeting(Random random) {
        return greetingLines.get(random.nextInt(greetingLines.size()));
    }

    public String randomReturnGreeting(Random random) {
        return returnGreetingLines.get(random.nextInt(returnGreetingLines.size()));
    }

    public String randomRumorDetail(Random random) {
        return rumorDetails.get(random.nextInt(rumorDetails.size()));
    }

    public String randomRumorSource(Random random) {
        return rumorSources.get(random.nextInt(rumorSources.size()));
    }

    public String randomSmalltalkOpener(Random random) {
        return smalltalkOpeners.get(random.nextInt(smalltalkOpeners.size()));
    }

    public String randomPerspectiveDetail(Random random) {
        return perspectiveDetails.get(random.nextInt(perspectiveDetails.size()));
    }

    public String randomFollowUpPrompt(Random random) {
        return followUpPrompts.get(random.nextInt(followUpPrompts.size()));
    }

    public String randomFollowUpResponse(Random random) {
        return followUpResponses.get(random.nextInt(followUpResponses.size()));
    }

    public String randomDecisivePrompt(Random random) {
        return decisivePrompts.get(random.nextInt(decisivePrompts.size()));
    }

    public String randomDecisiveResponse(Random random) {
        return decisiveResponses.get(random.nextInt(decisiveResponses.size()));
    }
}
