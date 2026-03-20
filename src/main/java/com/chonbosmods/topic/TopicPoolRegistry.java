package com.chonbosmods.topic;

import com.google.common.flogger.FluentLogger;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TopicPoolRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_PREFIX = "topics/pools/";

    public record SubjectEntry(String value, boolean plural, boolean questEligible) {}

    private final List<SubjectEntry> subjectFocuses = new ArrayList<>();
    private final List<String> greetingLines = new ArrayList<>();
    private final List<String> returnGreetingLines = new ArrayList<>();
    private final List<String> rumorDetails = new ArrayList<>();
    private final List<String> rumorSources = new ArrayList<>();
    private final List<String> smalltalkOpeners = new ArrayList<>();
    private final List<String> perspectiveDetails = new ArrayList<>();

    public void loadAll(@Nullable Path poolsDir) {
        // Load from classpath first (bundled resources)
        loadSubjectPoolFromClasspath(CLASSPATH_PREFIX + "subject_focuses.json");
        loadGreetingPoolFromClasspath(CLASSPATH_PREFIX + "greeting_lines.json");
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "rumor_details.json", rumorDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "rumor_sources.json", rumorSources);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "smalltalk_openers.json", smalltalkOpeners);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "perspective_details.json", perspectiveDetails);

        // Override with filesystem if available
        if (poolsDir != null && Files.isDirectory(poolsDir)) {
            loadSubjectPool(poolsDir.resolve("subject_focuses.json"));
            loadGreetingPool(poolsDir.resolve("greeting_lines.json"));
            loadStringPool(poolsDir.resolve("rumor_details.json"), rumorDetails);
            loadStringPool(poolsDir.resolve("rumor_sources.json"), rumorSources);
            loadStringPool(poolsDir.resolve("smalltalk_openers.json"), smalltalkOpeners);
            loadStringPool(poolsDir.resolve("perspective_details.json"), perspectiveDetails);
        }

        LOGGER.atInfo().log("Loaded topic pools: %d subjects, %d greetings, %d return greetings",
            subjectFocuses.size(), greetingLines.size(), returnGreetingLines.size());
    }

    private void loadSubjectPoolFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseSubjects(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load subject pool from classpath: %s", resource);
        }
    }

    private void loadGreetingPoolFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseGreetings(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load greeting pool from classpath: %s", resource);
        }
    }

    private void loadStringPoolFromClasspath(String resource, List<String> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : arr) target.add(el.getAsString());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load string pool from classpath: %s", resource);
        }
    }

    private void loadSubjectPool(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            subjectFocuses.clear();
            parseSubjects(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load subject pool: %s", file);
        }
    }

    private void loadGreetingPool(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            greetingLines.clear();
            returnGreetingLines.clear();
            parseGreetings(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load greeting pool: %s", file);
        }
    }

    private void loadStringPool(Path file, List<String> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
            target.clear();
            for (JsonElement el : arr) target.add(el.getAsString());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load string pool: %s", file);
        }
    }

    private void parseSubjects(JsonObject root) {
        for (JsonElement el : root.getAsJsonArray("subjects")) {
            JsonObject obj = el.getAsJsonObject();
            subjectFocuses.add(new SubjectEntry(
                obj.get("value").getAsString(),
                obj.has("plural") && obj.get("plural").getAsBoolean(),
                obj.has("questEligible") && obj.get("questEligible").getAsBoolean()
            ));
        }
    }

    private void parseGreetings(JsonObject root) {
        for (JsonElement el : root.getAsJsonArray("greetings")) greetingLines.add(el.getAsString());
        for (JsonElement el : root.getAsJsonArray("returnGreetings")) returnGreetingLines.add(el.getAsString());
    }

    // --- Random selection methods ---

    public SubjectEntry randomSubject(Random random) {
        if (subjectFocuses.isEmpty()) return new SubjectEntry("strange occurrence", false, false);
        return subjectFocuses.get(random.nextInt(subjectFocuses.size()));
    }

    public SubjectEntry randomQuestEligibleSubject(Random random) {
        List<SubjectEntry> eligible = subjectFocuses.stream()
            .filter(SubjectEntry::questEligible).toList();
        if (eligible.isEmpty()) return randomSubject(random);
        return eligible.get(random.nextInt(eligible.size()));
    }

    public String randomGreeting(Random random) {
        if (greetingLines.isEmpty()) return "Well met, traveler.";
        return greetingLines.get(random.nextInt(greetingLines.size()));
    }

    public String randomReturnGreeting(Random random) {
        if (returnGreetingLines.isEmpty()) return "Back again, I see.";
        return returnGreetingLines.get(random.nextInt(returnGreetingLines.size()));
    }

    public String randomRumorDetail(Random random) {
        if (rumorDetails.isEmpty()) return "something unusual";
        return rumorDetails.get(random.nextInt(rumorDetails.size()));
    }

    public String randomRumorSource(Random random) {
        if (rumorSources.isEmpty()) return "a passing traveler";
        return rumorSources.get(random.nextInt(rumorSources.size()));
    }

    public String randomSmalltalkOpener(Random random) {
        if (smalltalkOpeners.isEmpty()) return "You know what I think?";
        return smalltalkOpeners.get(random.nextInt(smalltalkOpeners.size()));
    }

    public String randomPerspectiveDetail(Random random) {
        if (perspectiveDetails.isEmpty()) return "it's been on my mind lately";
        return perspectiveDetails.get(random.nextInt(perspectiveDetails.size()));
    }
}
