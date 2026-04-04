package com.chonbosmods.topic;

import com.chonbosmods.dialogue.ValenceType;
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
                                boolean concrete, List<String> categories,
                                String poiType, List<String> questAffinities) {}

    private final List<SubjectEntry> subjectFocuses = new ArrayList<>();
    private final List<String> greetingLines = new ArrayList<>();
    private final List<String> returnGreetingLines = new ArrayList<>();
    private final List<String> perspectiveDetails = new ArrayList<>();

    // Drop-in pools
    private final List<String> timeRefs = new ArrayList<>();
    private final List<String> directions = new ArrayList<>();

    // Flavor pools (expanded template variables)
    private final List<String> foodTypes = new ArrayList<>();
    private final List<String> cropTypes = new ArrayList<>();
    private final List<String> wildlifeTypes = new ArrayList<>();
    private final Map<String, List<String>> resourceTypesByPoi = new LinkedHashMap<>();

    // Tone pools (bracket-keyed, valence-nested)
    private final Map<String, Map<String, List<String>>> toneOpeners = new LinkedHashMap<>();
    private final Map<String, Map<String, List<String>>> toneClosers = new LinkedHashMap<>();

    // Coherent triplet pools (v2)
    private final Map<String, List<PoolEntry>> coherentPools = new LinkedHashMap<>();

    public void loadAll(@Nullable Path poolsDir) {
        // Load from classpath first (bundled resources)
        loadSubjectPoolFromClasspath(CLASSPATH_PREFIX + "subject_focuses.json");
        loadGreetingPoolFromClasspath(CLASSPATH_PREFIX + "greeting_lines.json");
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "perspective_details.json", perspectiveDetails);

        // Drop-in pools
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "time_refs.json", timeRefs);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "directions.json", directions);

        // Flavor pools
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "food_types.json", foodTypes);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "crop_types.json", cropTypes);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "wildlife_types.json", wildlifeTypes);
        loadResourceTypesFromClasspath(CLASSPATH_PREFIX + "resource_types.json");

        // Tone pools (bracket-keyed)
        loadTonePoolFromClasspath(CLASSPATH_PREFIX + "tone_openers.json", toneOpeners);
        loadTonePoolFromClasspath(CLASSPATH_PREFIX + "tone_closers.json", toneClosers);

        // Override with filesystem if available
        if (poolsDir != null && Files.isDirectory(poolsDir)) {
            loadSubjectPool(poolsDir.resolve("subject_focuses.json"));
            loadGreetingPool(poolsDir.resolve("greeting_lines.json"));
            loadStringPool(poolsDir.resolve("perspective_details.json"), perspectiveDetails);

            // Drop-in pools
            loadStringPool(poolsDir.resolve("time_refs.json"), timeRefs);
            loadStringPool(poolsDir.resolve("directions.json"), directions);

            // Flavor pools
            loadStringPool(poolsDir.resolve("food_types.json"), foodTypes);
            loadStringPool(poolsDir.resolve("crop_types.json"), cropTypes);
            loadStringPool(poolsDir.resolve("wildlife_types.json"), wildlifeTypes);
            loadResourceTypes(poolsDir.resolve("resource_types.json"));

            // Tone pools (bracket-keyed)
            loadTonePool(poolsDir.resolve("tone_openers.json"), toneOpeners);
            loadTonePool(poolsDir.resolve("tone_closers.json"), toneClosers);
        }

        // Coherent triplet pools
        loadCoherentPools(poolsDir);

        LOGGER.atFine().log("Loaded topic pools: %d subjects, %d greetings, %d return greetings",
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

    private void loadResourceTypesFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseResourceTypes(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load resource types from classpath: %s", resource);
        }
    }

    private void loadResourceTypes(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            resourceTypesByPoi.clear();
            parseResourceTypes(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load resource types: %s", file);
        }
    }

    private void parseResourceTypes(JsonObject root) {
        for (var entry : root.entrySet()) {
            List<String> pool = new ArrayList<>();
            for (JsonElement el : entry.getValue().getAsJsonArray()) {
                pool.add(el.getAsString());
            }
            resourceTypesByPoi.put(entry.getKey(), pool);
        }
    }

    private void loadTonePoolFromClasspath(String resource, Map<String, Map<String, List<String>>> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseTonePool(root, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load tone pool from classpath: %s", resource);
        }
    }

    private void loadTonePool(Path file, Map<String, Map<String, List<String>>> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            target.clear();
            parseTonePool(root, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load tone pool: %s", file);
        }
    }

    private void parseTonePool(JsonObject root, Map<String, Map<String, List<String>>> target) {
        for (String bracket : root.keySet()) {
            JsonElement bracketEl = root.get(bracket);
            if (bracketEl.isJsonObject()) {
                // Nested valence lanes format
                JsonObject valenceObj = bracketEl.getAsJsonObject();
                Map<String, List<String>> lanes = new LinkedHashMap<>();
                for (String valence : valenceObj.keySet()) {
                    List<String> entries = new ArrayList<>();
                    for (JsonElement el : valenceObj.getAsJsonArray(valence)) {
                        entries.add(el.getAsString());
                    }
                    lanes.put(valence, entries);
                }
                target.put(bracket, lanes);
            } else if (bracketEl.isJsonArray()) {
                // Legacy flat array format: treat all as neutral
                Map<String, List<String>> lanes = new LinkedHashMap<>();
                List<String> entries = new ArrayList<>();
                for (JsonElement el : bracketEl.getAsJsonArray()) {
                    entries.add(el.getAsString());
                }
                lanes.put("neutral", entries);
                lanes.put("positive", new ArrayList<>());
                lanes.put("negative", new ArrayList<>());
                target.put(bracket, lanes);
            }
        }
    }

    private void parseSubjects(JsonObject root) {
        for (JsonElement el : root.getAsJsonArray("subjects")) {
            JsonObject obj = el.getAsJsonObject();
            String value = obj.get("value").getAsString();
            if (!obj.has("proper")) {
                LOGGER.atWarning().log("Subject entry '%s' missing 'proper' field, defaulting to false", value);
            }
            List<String> categories = new ArrayList<>();
            if (obj.has("categories")) {
                for (JsonElement cat : obj.getAsJsonArray("categories")) {
                    categories.add(cat.getAsString());
                }
            }
            boolean concrete = !obj.has("concrete") || obj.get("concrete").getAsBoolean();
            String poiType = obj.has("poiType") ? obj.get("poiType").getAsString() : "narrative_only";
            List<String> questAffinities = new ArrayList<>();
            if (obj.has("questAffinities")) {
                for (JsonElement aff : obj.getAsJsonArray("questAffinities")) {
                    questAffinities.add(aff.getAsString());
                }
            }
            subjectFocuses.add(new SubjectEntry(
                value,
                obj.has("plural") && obj.get("plural").getAsBoolean(),
                obj.has("proper") && obj.get("proper").getAsBoolean(),
                obj.has("questEligible") && obj.get("questEligible").getAsBoolean(),
                concrete,
                categories,
                poiType,
                questAffinities
            ));
        }
    }

    private void parseGreetings(JsonObject root) {
        for (JsonElement el : root.getAsJsonArray("greetings")) greetingLines.add(el.getAsString());
        for (JsonElement el : root.getAsJsonArray("returnGreetings")) returnGreetingLines.add(el.getAsString());
    }

    // --- Random selection methods ---

    public SubjectEntry randomSubject(Random random) {
        if (subjectFocuses.isEmpty()) return new SubjectEntry("strange occurrence", false, false, false, true, List.of(), "narrative_only", List.of());
        return subjectFocuses.get(random.nextInt(subjectFocuses.size()));
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

    public String randomPerspectiveDetail(Random random) {
        if (perspectiveDetails.isEmpty()) return "it's been on my mind lately";
        return perspectiveDetails.get(random.nextInt(perspectiveDetails.size()));
    }

    // --- Drop-in pool accessors ---

    public String randomTimeRef(Random random) {
        if (timeRefs.isEmpty()) return "not long ago";
        return timeRefs.get(random.nextInt(timeRefs.size()));
    }

    public String randomDirection(Random random) {
        if (directions.isEmpty()) return "out that way";
        return directions.get(random.nextInt(directions.size()));
    }

    // --- Tone pool accessors (bracket + valence filtered) ---

    /**
     * Select a tone opener with valence fallback chain:
     * requested valence lane -> neutral lane -> all entries in bracket.
     */
    public String randomToneOpener(String bracket, ValenceType valence, Random random) {
        return selectFromTonePool(toneOpeners, bracket, valence, random);
    }

    public String randomToneCloser(String bracket, ValenceType valence, Random random) {
        return selectFromTonePool(toneClosers, bracket, valence, random);
    }

    /** Legacy overload (no valence): defaults to NEUTRAL. */
    public String randomToneOpener(String bracket, Random random) {
        return randomToneOpener(bracket, ValenceType.NEUTRAL, random);
    }

    /** Legacy overload (no valence): defaults to NEUTRAL. */
    public String randomToneCloser(String bracket, Random random) {
        return randomToneCloser(bracket, ValenceType.NEUTRAL, random);
    }

    private String selectFromTonePool(Map<String, Map<String, List<String>>> pool,
                                       String bracket, ValenceType valence, Random random) {
        Map<String, List<String>> lanes = pool.get(bracket);
        if (lanes == null) return "";

        // Try requested valence lane
        String valenceKey = valence.name().toLowerCase();
        List<String> lane = lanes.get(valenceKey);
        if (lane != null && !lane.isEmpty()) {
            return lane.get(random.nextInt(lane.size()));
        }

        // Fallback: neutral lane
        List<String> neutralLane = lanes.get("neutral");
        if (neutralLane != null && !neutralLane.isEmpty()) {
            return neutralLane.get(random.nextInt(neutralLane.size()));
        }

        // Final fallback: all entries across all lanes
        List<String> all = new ArrayList<>();
        for (List<String> l : lanes.values()) all.addAll(l);
        if (all.isEmpty()) return "";
        return all.get(random.nextInt(all.size()));
    }

    // --- Coherent triplet pool methods ---

    public void loadCoherentPools(@Nullable Path poolsDir) {
        // V2 pools (archived, still loaded for backward compatibility)
        String[] v2Ids = {
            "danger", "sighting", "treasure", "corruption", "conflict",
            "disappearance", "migration", "omen", "weather", "trade",
            "craftsmanship", "community", "nature", "nostalgia", "curiosity", "festival"
        };
        for (String id : v2Ids) {
            loadCoherentPool(id, "v2", poolsDir);
        }

        // V3 pools (label-based category system with template variables)
        String[] v3Ids = {
            "mundane_daily_life", "npc_opinions", "settlement_pride",
            "poi_awareness", "creature_complaints", "distant_rumors",
            "family_talk", "folk_wisdom", "idle_musings", "food_and_meals",
            "travelers_and_trade", "night_watch", "work_life", "old_times"
        };
        for (String id : v3Ids) {
            loadCoherentPool(id, "v3", poolsDir);
        }

        LOGGER.atFine().log("Loaded %d coherent pools (%d total entries)",
            coherentPools.size(),
            coherentPools.values().stream().mapToInt(List::size).sum());
    }

    private void loadCoherentPool(String templateId, String versionDir, @Nullable Path poolsDir) {
        String classpathResource = "topics/pools/" + versionDir + "/" + templateId + ".json";
        if (poolsDir != null) {
            Path file = poolsDir.resolve(versionDir + "/" + templateId + ".json");
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    parseCoherentPool(templateId, JsonParser.parseReader(reader).getAsJsonObject());
                    return;
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load coherent pool: %s", file);
                }
            }
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (is == null) return;
            parseCoherentPool(templateId, JsonParser.parseReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load coherent pool from classpath: %s", classpathResource);
        }
    }

    private void parseCoherentPool(String templateId, JsonObject root) {
        JsonArray entries = root.getAsJsonArray("entries");
        if (entries == null) return;
        List<PoolEntry> pool = new ArrayList<>();
        for (JsonElement el : entries) {
            JsonObject obj = el.getAsJsonObject();
            int id = obj.get("id").getAsInt();
            String intro = obj.get("intro").getAsString();
            List<String> details = new ArrayList<>();
            for (JsonElement d : obj.getAsJsonArray("details")) details.add(d.getAsString());
            List<String> reactions = new ArrayList<>();
            for (JsonElement r : obj.getAsJsonArray("reactions")) reactions.add(r.getAsString());
            PoolEntry.StatCheck statCheck = null;
            if (obj.has("statCheck") && !obj.get("statCheck").isJsonNull()) {
                JsonObject sc = obj.getAsJsonObject("statCheck");
                statCheck = new PoolEntry.StatCheck(sc.get("pass").getAsString(), sc.get("fail").getAsString());
            }
            ValenceType valence = ValenceType.NEUTRAL;
            if (obj.has("valence")) {
                valence = ValenceType.fromString(obj.get("valence").getAsString());
            }
            pool.add(new PoolEntry(id, intro, details, reactions, statCheck, valence));
        }
        coherentPools.put(templateId, pool);
    }

    public List<PoolEntry> getCoherentPool(String templateId) {
        return coherentPools.getOrDefault(templateId, List.of());
    }

    // --- List accessors for shared pools ---

    public List<String> getTimeRefs() { return timeRefs; }
    public List<String> getDirections() { return directions; }
    public List<String> getFoodTypes() { return foodTypes; }
    public List<String> getCropTypes() { return cropTypes; }
    public List<String> getWildlifeTypes() { return wildlifeTypes; }
    public List<String> getResourceTypes(String poiKey) {
        List<String> pool = resourceTypesByPoi.get(poiKey);
        return (pool != null && !pool.isEmpty()) ? pool : resourceTypesByPoi.getOrDefault("general", List.of());
    }
    public List<String> getToneOpeners(String bracket) {
        return flattenLanes(toneOpeners.get(bracket));
    }
    public List<String> getToneClosers(String bracket) {
        return flattenLanes(toneClosers.get(bracket));
    }

    private List<String> flattenLanes(Map<String, List<String>> lanes) {
        if (lanes == null) return List.of();
        List<String> all = new ArrayList<>();
        for (List<String> l : lanes.values()) all.addAll(l);
        return all;
    }
}
