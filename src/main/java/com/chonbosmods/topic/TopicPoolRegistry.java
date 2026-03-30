package com.chonbosmods.topic;

import com.chonbosmods.stats.Skill;
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

    public record SubjectEntry(String value, boolean plural, boolean proper, boolean questEligible,
                                boolean concrete, List<String> categories) {}

    public record IntentDef(boolean deepens, String pool) {}

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

    // Fragment pools: Layer 0 (new topic-matched)
    private final List<String> weatherObservations = new ArrayList<>();
    private final List<String> craftObservations = new ArrayList<>();
    private final List<String> communityObservations = new ArrayList<>();
    private final List<String> natureObservations = new ArrayList<>();
    private final List<String> nostalgiaObservations = new ArrayList<>();
    private final List<String> curiosityObservations = new ArrayList<>();
    private final List<String> festivalObservations = new ArrayList<>();
    private final List<String> treasureRumors = new ArrayList<>();
    private final List<String> conflictRumors = new ArrayList<>();

    // Fragment pools: Layer 1
    private final List<String> creatureDetails = new ArrayList<>();
    private final List<String> eventDetails = new ArrayList<>();
    private final List<String> tradeDetails = new ArrayList<>();
    private final List<String> locationDetails = new ArrayList<>();

    // Fragment pools: Layer 1 (new topic-matched)
    private final List<String> weatherDetails = new ArrayList<>();
    private final List<String> craftDetails = new ArrayList<>();
    private final List<String> communityDetails = new ArrayList<>();
    private final List<String> natureDetails = new ArrayList<>();
    private final List<String> nostalgiaDetails = new ArrayList<>();
    private final List<String> curiosityDetails = new ArrayList<>();
    private final List<String> festivalDetails = new ArrayList<>();
    private final List<String> treasureDetails = new ArrayList<>();
    private final List<String> conflictDetails = new ArrayList<>();

    // Fragment pools: Layer 2
    private final Map<String, List<String>> localOpinions = new LinkedHashMap<>();
    private final Map<String, List<String>> personalReactions = new LinkedHashMap<>();
    private final List<String> dangerAssessments = new ArrayList<>();

    // Tone pools (bracket-keyed)
    private final Map<String, List<String>> toneOpeners = new LinkedHashMap<>();
    private final Map<String, List<String>> toneClosers = new LinkedHashMap<>();

    // Stat check pools: outer key = category name ("RUMORS"|"SMALLTALK"), inner key = skill name
    private final Map<String, Map<String, List<String>>> statCheckPrompts = new LinkedHashMap<>();
    private final Map<String, Map<String, List<String>>> statCheckPass = new LinkedHashMap<>();
    private final Map<String, Map<String, List<String>>> statCheckFail = new LinkedHashMap<>();

    // Intent definitions and prompt pools
    private final Map<String, IntentDef> intentDefs = new LinkedHashMap<>();
    private final Map<String, List<String>> promptPools = new LinkedHashMap<>();
    private final List<String> deepenerPool = new ArrayList<>();
    private int maxL1 = 3;

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

        // Fragment pools: Layer 0 (new topic-matched)
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "weather_observations.json", weatherObservations);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "craft_observations.json", craftObservations);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "community_observations.json", communityObservations);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "nature_observations.json", natureObservations);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "nostalgia_observations.json", nostalgiaObservations);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "curiosity_observations.json", curiosityObservations);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "festival_observations.json", festivalObservations);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "treasure_rumors.json", treasureRumors);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "conflict_rumors.json", conflictRumors);

        // Fragment pools: Layer 1
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "creature_details.json", creatureDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "event_details.json", eventDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "trade_details.json", tradeDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "location_details.json", locationDetails);

        // Fragment pools: Layer 1 (new topic-matched)
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "weather_details.json", weatherDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "craft_details.json", craftDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "community_details.json", communityDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "nature_details.json", natureDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "nostalgia_details.json", nostalgiaDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "curiosity_details.json", curiosityDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "festival_details.json", festivalDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "treasure_details.json", treasureDetails);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "conflict_details.json", conflictDetails);

        // Fragment pools: Layer 2
        loadTonePoolFromClasspath(CLASSPATH_PREFIX + "local_opinions.json", localOpinions);
        loadTonePoolFromClasspath(CLASSPATH_PREFIX + "personal_reactions.json", personalReactions);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "danger_assessments.json", dangerAssessments);

        // Tone pools (bracket-keyed)
        loadTonePoolFromClasspath(CLASSPATH_PREFIX + "tone_openers.json", toneOpeners);
        loadTonePoolFromClasspath(CLASSPATH_PREFIX + "tone_closers.json", toneClosers);

        // Stat check pools (per-category)
        loadSkillPoolFromClasspath("topics/Rumors/pools/stat_check_prompts.json", "RUMORS", statCheckPrompts);
        loadSkillPoolFromClasspath("topics/Rumors/pools/stat_check_pass.json", "RUMORS", statCheckPass);
        loadSkillPoolFromClasspath("topics/Rumors/pools/stat_check_fail.json", "RUMORS", statCheckFail);
        loadSkillPoolFromClasspath("topics/SmallTalk/pools/stat_check_prompts.json", "SMALLTALK", statCheckPrompts);
        loadSkillPoolFromClasspath("topics/SmallTalk/pools/stat_check_pass.json", "SMALLTALK", statCheckPass);
        loadSkillPoolFromClasspath("topics/SmallTalk/pools/stat_check_fail.json", "SMALLTALK", statCheckFail);

        // Intent definitions and prompt pools
        loadIntentDefsFromClasspath("topics/intents.json");

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

            // Fragment pools: Layer 0 (new topic-matched)
            loadStringPool(poolsDir.resolve("weather_observations.json"), weatherObservations);
            loadStringPool(poolsDir.resolve("craft_observations.json"), craftObservations);
            loadStringPool(poolsDir.resolve("community_observations.json"), communityObservations);
            loadStringPool(poolsDir.resolve("nature_observations.json"), natureObservations);
            loadStringPool(poolsDir.resolve("nostalgia_observations.json"), nostalgiaObservations);
            loadStringPool(poolsDir.resolve("curiosity_observations.json"), curiosityObservations);
            loadStringPool(poolsDir.resolve("festival_observations.json"), festivalObservations);
            loadStringPool(poolsDir.resolve("treasure_rumors.json"), treasureRumors);
            loadStringPool(poolsDir.resolve("conflict_rumors.json"), conflictRumors);

            // Fragment pools: Layer 1
            loadStringPool(poolsDir.resolve("creature_details.json"), creatureDetails);
            loadStringPool(poolsDir.resolve("event_details.json"), eventDetails);
            loadStringPool(poolsDir.resolve("trade_details.json"), tradeDetails);
            loadStringPool(poolsDir.resolve("location_details.json"), locationDetails);

            // Fragment pools: Layer 1 (new topic-matched)
            loadStringPool(poolsDir.resolve("weather_details.json"), weatherDetails);
            loadStringPool(poolsDir.resolve("craft_details.json"), craftDetails);
            loadStringPool(poolsDir.resolve("community_details.json"), communityDetails);
            loadStringPool(poolsDir.resolve("nature_details.json"), natureDetails);
            loadStringPool(poolsDir.resolve("nostalgia_details.json"), nostalgiaDetails);
            loadStringPool(poolsDir.resolve("curiosity_details.json"), curiosityDetails);
            loadStringPool(poolsDir.resolve("festival_details.json"), festivalDetails);
            loadStringPool(poolsDir.resolve("treasure_details.json"), treasureDetails);
            loadStringPool(poolsDir.resolve("conflict_details.json"), conflictDetails);

            // Fragment pools: Layer 2
            loadTonePool(poolsDir.resolve("local_opinions.json"), localOpinions);
            loadTonePool(poolsDir.resolve("personal_reactions.json"), personalReactions);
            loadStringPool(poolsDir.resolve("danger_assessments.json"), dangerAssessments);

            // Tone pools (bracket-keyed)
            loadTonePool(poolsDir.resolve("tone_openers.json"), toneOpeners);
            loadTonePool(poolsDir.resolve("tone_closers.json"), toneClosers);

            // Stat check pools filesystem override
            Path topicsDir = poolsDir.getParent();
            loadSkillPool(topicsDir.resolve("Rumors/pools/stat_check_prompts.json"), "RUMORS", statCheckPrompts);
            loadSkillPool(topicsDir.resolve("Rumors/pools/stat_check_pass.json"), "RUMORS", statCheckPass);
            loadSkillPool(topicsDir.resolve("Rumors/pools/stat_check_fail.json"), "RUMORS", statCheckFail);
            loadSkillPool(topicsDir.resolve("SmallTalk/pools/stat_check_prompts.json"), "SMALLTALK", statCheckPrompts);
            loadSkillPool(topicsDir.resolve("SmallTalk/pools/stat_check_pass.json"), "SMALLTALK", statCheckPass);
            loadSkillPool(topicsDir.resolve("SmallTalk/pools/stat_check_fail.json"), "SMALLTALK", statCheckFail);

            // Intent definitions filesystem override
            loadIntentDefs(topicsDir.resolve("intents.json"));
        }

        LOGGER.atFine().log("Loaded topic pools: %d subjects, %d greetings, %d return greetings",
            subjectFocuses.size(), greetingLines.size(), returnGreetingLines.size());

        validateL0Capitalization();
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

    private void loadSkillPoolFromClasspath(String resource, String category,
                                             Map<String, Map<String, List<String>>> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseSkillPool(root, category, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load skill pool from classpath: %s", resource);
        }
    }

    private void loadSkillPool(Path file, String category,
                                 Map<String, Map<String, List<String>>> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            target.remove(category);
            parseSkillPool(root, category, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load skill pool: %s", file);
        }
    }

    private void parseSkillPool(JsonObject root, String category,
                                  Map<String, Map<String, List<String>>> target) {
        Map<String, List<String>> skillMap = target.computeIfAbsent(category, k -> new LinkedHashMap<>());
        for (String skill : root.keySet()) {
            List<String> entries = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray(skill)) {
                entries.add(el.getAsString());
            }
            skillMap.put(skill, entries);
        }
    }

    private void parseSubjects(JsonObject root) {
        for (JsonElement el : root.getAsJsonArray("subjects")) {
            JsonObject obj = el.getAsJsonObject();
            List<String> categories = new ArrayList<>();
            if (obj.has("categories")) {
                for (JsonElement cat : obj.getAsJsonArray("categories")) {
                    categories.add(cat.getAsString());
                }
            }
            boolean concrete = !obj.has("concrete") || obj.get("concrete").getAsBoolean();
            subjectFocuses.add(new SubjectEntry(
                obj.get("value").getAsString(),
                obj.has("plural") && obj.get("plural").getAsBoolean(),
                obj.has("proper") && obj.get("proper").getAsBoolean(),
                obj.has("questEligible") && obj.get("questEligible").getAsBoolean(),
                concrete,
                categories
            ));
        }
    }

    private void parseGreetings(JsonObject root) {
        for (JsonElement el : root.getAsJsonArray("greetings")) greetingLines.add(el.getAsString());
        for (JsonElement el : root.getAsJsonArray("returnGreetings")) returnGreetingLines.add(el.getAsString());
    }

    private void loadIntentDefsFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseIntentDefs(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load intent definitions from classpath: %s", resource);
        }
    }

    private void loadIntentDefs(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            intentDefs.clear();
            promptPools.clear();
            deepenerPool.clear();
            parseIntentDefs(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load intent definitions: %s", file);
        }
    }

    private void parseIntentDefs(JsonObject root) {
        JsonObject intents = root.getAsJsonObject("intents");
        for (String key : intents.keySet()) {
            JsonObject def = intents.getAsJsonObject(key);
            boolean deepens = def.get("deepens").getAsBoolean();
            String poolName = def.get("pool").getAsString();
            intentDefs.put(key, new IntentDef(deepens, poolName));

            // Load the prompt pool for this intent
            List<String> pool = new ArrayList<>();
            loadStringPoolFromClasspath(CLASSPATH_PREFIX + poolName + ".json", pool);
            promptPools.put(key, pool);
        }

        // Load deepener pool
        if (root.has("deepenerPool")) {
            String deepenerPoolName = root.get("deepenerPool").getAsString();
            loadStringPoolFromClasspath(CLASSPATH_PREFIX + deepenerPoolName + ".json", deepenerPool);
        }

        // Max L1 cap
        if (root.has("maxL1")) {
            maxL1 = root.get("maxL1").getAsInt();
        }

        LOGGER.atFine().log("Loaded %d intent definitions, %d deepener prompts, maxL1=%d",
            intentDefs.size(), deepenerPool.size(), maxL1);
    }

    // --- Random selection methods ---

    public SubjectEntry randomSubject(Random random) {
        if (subjectFocuses.isEmpty()) return new SubjectEntry("strange occurrence", false, false, false, true, List.of());
        return subjectFocuses.get(random.nextInt(subjectFocuses.size()));
    }

    public SubjectEntry randomSubjectForTemplate(String templateId, Random random) {
        // Extract category from template ID: "smalltalk_weather" -> "weather", "rumor_danger" -> "danger"
        String category = templateId.contains("_") ? templateId.substring(templateId.indexOf('_') + 1) : templateId;
        List<SubjectEntry> matching = subjectFocuses.stream()
            .filter(e -> e.categories().contains(category))
            .toList();
        if (matching.isEmpty()) return randomSubject(random); // Fallback to unfiltered
        return matching.get(random.nextInt(matching.size()));
    }

    public SubjectEntry randomSubjectForCategory(String targetCategory, Random random) {
        List<SubjectEntry> matching = subjectFocuses.stream()
            .filter(s -> s.categories().contains(targetCategory))
            .toList();
        if (matching.isEmpty()) return randomSubject(random);
        return matching.get(random.nextInt(matching.size()));
    }

    public SubjectEntry randomSubjectForCategoryExcluding(String targetCategory, Set<String> usedValues, Random random) {
        List<SubjectEntry> matching = subjectFocuses.stream()
            .filter(s -> s.categories().contains(targetCategory))
            .filter(s -> !usedValues.contains(s.value()))
            .toList();
        if (matching.isEmpty()) {
            // Fallback: any subject with the target category (even if already used), or any subject at all
            List<SubjectEntry> anyMatching = subjectFocuses.stream()
                .filter(s -> s.categories().contains(targetCategory))
                .toList();
            if (anyMatching.isEmpty()) return randomSubject(random);
            return anyMatching.get(random.nextInt(anyMatching.size()));
        }
        return matching.get(random.nextInt(matching.size()));
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

    // --- Fragment pool accessors: Layer 0 (new topic-matched) ---

    public String randomWeatherObservation(Random random) {
        if (weatherObservations.isEmpty()) return "the weather has been unpredictable lately";
        return weatherObservations.get(random.nextInt(weatherObservations.size()));
    }

    public String randomCraftObservation(Random random) {
        if (craftObservations.isEmpty()) return "the workshop has been busy";
        return craftObservations.get(random.nextInt(craftObservations.size()));
    }

    public String randomCommunityObservation(Random random) {
        if (communityObservations.isEmpty()) return "people have been talking";
        return communityObservations.get(random.nextInt(communityObservations.size()));
    }

    public String randomNatureObservation(Random random) {
        if (natureObservations.isEmpty()) return "the wilds have been restless";
        return natureObservations.get(random.nextInt(natureObservations.size()));
    }

    public String randomNostalgiaObservation(Random random) {
        if (nostalgiaObservations.isEmpty()) return "things were different before";
        return nostalgiaObservations.get(random.nextInt(nostalgiaObservations.size()));
    }

    public String randomCuriosityObservation(Random random) {
        if (curiosityObservations.isEmpty()) return "something odd has been happening";
        return curiosityObservations.get(random.nextInt(curiosityObservations.size()));
    }

    public String randomFestivalObservation(Random random) {
        if (festivalObservations.isEmpty()) return "there is talk of a celebration";
        return festivalObservations.get(random.nextInt(festivalObservations.size()));
    }

    public String randomTreasureRumor(Random random) {
        if (treasureRumors.isEmpty()) return "someone found something valuable out there";
        return treasureRumors.get(random.nextInt(treasureRumors.size()));
    }

    public String randomConflictRumor(Random random) {
        if (conflictRumors.isEmpty()) return "tensions have been rising between neighbors";
        return conflictRumors.get(random.nextInt(conflictRumors.size()));
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

    // --- Fragment pool accessors: Layer 1 (new topic-matched) ---

    public String randomWeatherDetail(Random random) {
        if (weatherDetails.isEmpty()) return "It has been like this for days.";
        return weatherDetails.get(random.nextInt(weatherDetails.size()));
    }

    public String randomCraftDetail(Random random) {
        if (craftDetails.isEmpty()) return "The work goes on, one way or another.";
        return craftDetails.get(random.nextInt(craftDetails.size()));
    }

    public String randomCommunityDetail(Random random) {
        if (communityDetails.isEmpty()) return "People have their opinions.";
        return communityDetails.get(random.nextInt(communityDetails.size()));
    }

    public String randomNatureDetail(Random random) {
        if (natureDetails.isEmpty()) return "The wilds are full of surprises.";
        return natureDetails.get(random.nextInt(natureDetails.size()));
    }

    public String randomNostalgiaDetail(Random random) {
        if (nostalgiaDetails.isEmpty()) return "Times change whether we want them to or not.";
        return nostalgiaDetails.get(random.nextInt(nostalgiaDetails.size()));
    }

    public String randomCuriosityDetail(Random random) {
        if (curiosityDetails.isEmpty()) return "Nobody seems to have an explanation.";
        return curiosityDetails.get(random.nextInt(curiosityDetails.size()));
    }

    public String randomFestivalDetail(Random random) {
        if (festivalDetails.isEmpty()) return "The preparations are well underway.";
        return festivalDetails.get(random.nextInt(festivalDetails.size()));
    }

    public String randomTreasureDetail(Random random) {
        if (treasureDetails.isEmpty()) return "That is all I know about it.";
        return treasureDetails.get(random.nextInt(treasureDetails.size()));
    }

    public String randomConflictDetail(Random random) {
        if (conflictDetails.isEmpty()) return "It is a delicate situation.";
        return conflictDetails.get(random.nextInt(conflictDetails.size()));
    }

    // --- Fragment pool accessors: Layer 2 ---

    public String randomLocalOpinion(String bracket, Random random) {
        List<String> entries = localOpinions.getOrDefault(bracket, List.of());
        if (entries.isEmpty()) return "Folk have their opinions, as always.";
        return entries.get(random.nextInt(entries.size()));
    }

    public String randomPersonalReaction(String bracket, Random random) {
        List<String> entries = personalReactions.getOrDefault(bracket, List.of());
        if (entries.isEmpty()) return "I try not to think about it too much.";
        return entries.get(random.nextInt(entries.size()));
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

    // --- Stat check pool accessors ---

    public String randomStatCheckPrompt(TopicCategory category, Skill skill, Random random) {
        Map<String, List<String>> skillMap = statCheckPrompts.getOrDefault(category.name(), Map.of());
        List<String> entries = skillMap.getOrDefault(skill.name(), List.of());
        if (entries.isEmpty()) return "Something about this doesn't add up.";
        return entries.get(random.nextInt(entries.size()));
    }

    public String randomStatCheckPass(TopicCategory category, Skill skill, Random random) {
        Map<String, List<String>> skillMap = statCheckPass.getOrDefault(category.name(), Map.of());
        List<String> entries = skillMap.getOrDefault(skill.name(), List.of());
        if (entries.isEmpty()) return "...Fine. You got me. There's more to it.";
        return entries.get(random.nextInt(entries.size()));
    }

    public String randomStatCheckFail(TopicCategory category, Skill skill, Random random) {
        Map<String, List<String>> skillMap = statCheckFail.getOrDefault(category.name(), Map.of());
        List<String> entries = skillMap.getOrDefault(skill.name(), List.of());
        if (entries.isEmpty()) return "I've told you everything I know.";
        return entries.get(random.nextInt(entries.size()));
    }

    // --- Intent and prompt pool accessors ---

    public IntentDef getIntentDef(String intent) {
        return intentDefs.get(intent);
    }

    public int getMaxL1() {
        return maxL1;
    }

    public String randomPromptForIntent(String intent, Random random) {
        List<String> pool = promptPools.getOrDefault(intent, List.of());
        if (pool.isEmpty()) return "Tell me more.";
        return pool.get(random.nextInt(pool.size()));
    }

    public String randomDeepener(Random random) {
        if (deepenerPool.isEmpty()) return "What do you make of all that?";
        return deepenerPool.get(random.nextInt(deepenerPool.size()));
    }

    /**
     * Draw a deepener prompt, avoiding entries in the exclusion set.
     * Falls back to any entry if all have been used.
     */
    public String randomDeepenerExcluding(Set<String> used, Random random) {
        if (deepenerPool.isEmpty()) return "What do you make of all that?";
        List<String> available = deepenerPool.stream()
            .filter(d -> !used.contains(d))
            .toList();
        if (available.isEmpty()) {
            return deepenerPool.get(random.nextInt(deepenerPool.size()));
        }
        return available.get(random.nextInt(available.size()));
    }

    /**
     * Validate that all L0 pool entries start with an uppercase letter.
     * Logs a warning for any entry that starts lowercase, which would look
     * wrong when used at the beginning of a sentence in dialogue.
     */
    private void validateL0Capitalization() {
        Map<String, List<String>> l0Pools = Map.ofEntries(
            Map.entry("creatureSightings", creatureSightings),
            Map.entry("strangeEvents", strangeEvents),
            Map.entry("tradeGossip", tradeGossip),
            Map.entry("localComplaints", localComplaints),
            Map.entry("travelerNews", travelerNews),
            Map.entry("weatherObservations", weatherObservations),
            Map.entry("craftObservations", craftObservations),
            Map.entry("communityObservations", communityObservations),
            Map.entry("natureObservations", natureObservations),
            Map.entry("nostalgiaObservations", nostalgiaObservations),
            Map.entry("curiosityObservations", curiosityObservations),
            Map.entry("festivalObservations", festivalObservations),
            Map.entry("treasureRumors", treasureRumors),
            Map.entry("conflictRumors", conflictRumors)
        );

        for (var entry : l0Pools.entrySet()) {
            for (String value : entry.getValue()) {
                if (!value.isEmpty() && Character.isLowerCase(value.charAt(0))) {
                    LOGGER.atWarning().log("L0 pool entry starts lowercase: '%s' in pool %s", value, entry.getKey());
                }
            }
        }
    }
}
