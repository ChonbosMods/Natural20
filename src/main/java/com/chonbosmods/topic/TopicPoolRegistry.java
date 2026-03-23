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

    // Drop-in pools (new)
    private final List<String> timeRefs = new ArrayList<>();
    private final List<String> directions = new ArrayList<>();

    // Fragment pools: Layer 0
    private final List<String> creatureSightings = new ArrayList<>();
    private final List<String> strangeEvents = new ArrayList<>();
    private final List<String> tradeGossip = new ArrayList<>();
    private final List<String> localComplaints = new ArrayList<>();
    private final List<String> travelerNews = new ArrayList<>();

    // Fragment pools: Layer 1
    private final List<String> creatureDetails = new ArrayList<>();
    private final List<String> eventDetails = new ArrayList<>();
    private final List<String> tradeDetails = new ArrayList<>();
    private final List<String> locationDetails = new ArrayList<>();

    // Fragment pools: Layer 2
    private final List<String> localOpinions = new ArrayList<>();
    private final List<String> personalReactions = new ArrayList<>();
    private final List<String> dangerAssessments = new ArrayList<>();

    // Tone pools (bracket-keyed)
    private final Map<String, List<String>> toneOpeners = new LinkedHashMap<>();
    private final Map<String, List<String>> toneClosers = new LinkedHashMap<>();

    public void loadAll(@Nullable Path poolsDir) {
        // Load from classpath first (bundled resources)
        loadSubjectPoolFromClasspath(CLASSPATH_PREFIX + "subject_focuses.json");
        loadGreetingPoolFromClasspath(CLASSPATH_PREFIX + "greeting_lines.json");
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "rumor_details.json", rumorDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "rumor_sources.json", rumorSources);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "smalltalk_openers.json", smalltalkOpeners);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "perspective_details.json", perspectiveDetails);

        // New drop-in pools
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "time_refs.json", timeRefs);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "directions.json", directions);

        // Fragment pools: Layer 0
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "creature_sightings.json", creatureSightings);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "strange_events.json", strangeEvents);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "trade_gossip.json", tradeGossip);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "local_complaints.json", localComplaints);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "traveler_news.json", travelerNews);

        // Fragment pools: Layer 1
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "creature_details.json", creatureDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "event_details.json", eventDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "trade_details.json", tradeDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "location_details.json", locationDetails);

        // Fragment pools: Layer 2
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "local_opinions.json", localOpinions);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "personal_reactions.json", personalReactions);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "danger_assessments.json", dangerAssessments);

        // Tone pools (bracket-keyed)
        loadTonePoolFromClasspath(CLASSPATH_PREFIX + "tone_openers.json", toneOpeners);
        loadTonePoolFromClasspath(CLASSPATH_PREFIX + "tone_closers.json", toneClosers);

        // Override with filesystem if available
        if (poolsDir != null && Files.isDirectory(poolsDir)) {
            loadSubjectPool(poolsDir.resolve("subject_focuses.json"));
            loadGreetingPool(poolsDir.resolve("greeting_lines.json"));
            loadStringPool(poolsDir.resolve("rumor_details.json"), rumorDetails);
            loadStringPool(poolsDir.resolve("rumor_sources.json"), rumorSources);
            loadStringPool(poolsDir.resolve("smalltalk_openers.json"), smalltalkOpeners);
            loadStringPool(poolsDir.resolve("perspective_details.json"), perspectiveDetails);

            // New drop-in pools
            loadStringPool(poolsDir.resolve("time_refs.json"), timeRefs);
            loadStringPool(poolsDir.resolve("directions.json"), directions);

            // Fragment pools: Layer 0
            loadStringPool(poolsDir.resolve("creature_sightings.json"), creatureSightings);
            loadStringPool(poolsDir.resolve("strange_events.json"), strangeEvents);
            loadStringPool(poolsDir.resolve("trade_gossip.json"), tradeGossip);
            loadStringPool(poolsDir.resolve("local_complaints.json"), localComplaints);
            loadStringPool(poolsDir.resolve("traveler_news.json"), travelerNews);

            // Fragment pools: Layer 1
            loadStringPool(poolsDir.resolve("creature_details.json"), creatureDetails);
            loadStringPool(poolsDir.resolve("event_details.json"), eventDetails);
            loadStringPool(poolsDir.resolve("trade_details.json"), tradeDetails);
            loadStringPool(poolsDir.resolve("location_details.json"), locationDetails);

            // Fragment pools: Layer 2
            loadStringPool(poolsDir.resolve("local_opinions.json"), localOpinions);
            loadStringPool(poolsDir.resolve("personal_reactions.json"), personalReactions);
            loadStringPool(poolsDir.resolve("danger_assessments.json"), dangerAssessments);

            // Tone pools (bracket-keyed)
            loadTonePool(poolsDir.resolve("tone_openers.json"), toneOpeners);
            loadTonePool(poolsDir.resolve("tone_closers.json"), toneClosers);
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

    private void loadTonePoolFromClasspath(String resource, Map<String, List<String>> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseTonePool(root, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load tone pool from classpath: %s", resource);
        }
    }

    private void loadTonePool(Path file, Map<String, List<String>> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            target.clear();
            parseTonePool(root, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load tone pool: %s", file);
        }
    }

    private void parseTonePool(JsonObject root, Map<String, List<String>> target) {
        for (String bracket : root.keySet()) {
            List<String> entries = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray(bracket)) {
                entries.add(el.getAsString());
            }
            target.put(bracket, entries);
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

    // --- New drop-in pool accessors ---

    public String randomTimeRef(Random random) {
        if (timeRefs.isEmpty()) return "not long ago";
        return timeRefs.get(random.nextInt(timeRefs.size()));
    }

    public String randomDirection(Random random) {
        if (directions.isEmpty()) return "out that way";
        return directions.get(random.nextInt(directions.size()));
    }

    // --- Fragment pool accessors: Layer 0 ---

    public String randomCreatureSighting(Random random) {
        if (creatureSightings.isEmpty()) return "something moving out past the ridge";
        return creatureSightings.get(random.nextInt(creatureSightings.size()));
    }

    public String randomStrangeEvent(Random random) {
        if (strangeEvents.isEmpty()) return "something no one could explain";
        return strangeEvents.get(random.nextInt(strangeEvents.size()));
    }

    public String randomTradeGossip(Random random) {
        if (tradeGossip.isEmpty()) return "trade hasn't been what it used to be";
        return tradeGossip.get(random.nextInt(tradeGossip.size()));
    }

    public String randomLocalComplaint(Random random) {
        if (localComplaints.isEmpty()) return "things aren't as good as they could be around here";
        return localComplaints.get(random.nextInt(localComplaints.size()));
    }

    public String randomTravelerNews(Random random) {
        if (travelerNews.isEmpty()) return "word from the road is that things are changing";
        return travelerNews.get(random.nextInt(travelerNews.size()));
    }

    // --- Fragment pool accessors: Layer 1 ---

    public String randomCreatureDetail(Random random) {
        if (creatureDetails.isEmpty()) return "That's all I know about it.";
        return creatureDetails.get(random.nextInt(creatureDetails.size()));
    }

    public String randomEventDetail(Random random) {
        if (eventDetails.isEmpty()) return "I wish I could tell you more.";
        return eventDetails.get(random.nextInt(eventDetails.size()));
    }

    public String randomTradeDetail(Random random) {
        if (tradeDetails.isEmpty()) return "It's hard to say what will happen next.";
        return tradeDetails.get(random.nextInt(tradeDetails.size()));
    }

    public String randomLocationDetail(Random random) {
        if (locationDetails.isEmpty()) return "Somewhere out past the settlement, that's all I know.";
        return locationDetails.get(random.nextInt(locationDetails.size()));
    }

    // --- Fragment pool accessors: Layer 2 ---

    public String randomLocalOpinion(Random random) {
        if (localOpinions.isEmpty()) return "Folk have their opinions, as always.";
        return localOpinions.get(random.nextInt(localOpinions.size()));
    }

    public String randomPersonalReaction(Random random) {
        if (personalReactions.isEmpty()) return "I try not to think about it too much.";
        return personalReactions.get(random.nextInt(personalReactions.size()));
    }

    public String randomDangerAssessment(Random random) {
        if (dangerAssessments.isEmpty()) return "Best to be careful, that's all I'll say.";
        return dangerAssessments.get(random.nextInt(dangerAssessments.size()));
    }

    // --- Tone pool accessors (bracket-filtered) ---

    public String randomToneOpener(String bracket, Random random) {
        List<String> entries = toneOpeners.getOrDefault(bracket, List.of());
        if (entries.isEmpty()) return "";
        return entries.get(random.nextInt(entries.size()));
    }

    public String randomToneCloser(String bracket, Random random) {
        List<String> entries = toneClosers.getOrDefault(bracket, List.of());
        if (entries.isEmpty()) return "";
        return entries.get(random.nextInt(entries.size()));
    }
}
