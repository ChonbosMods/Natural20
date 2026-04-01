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

    // Tone pools (bracket-keyed)
    private final Map<String, List<String>> toneOpeners = new LinkedHashMap<>();
    private final Map<String, List<String>> toneClosers = new LinkedHashMap<>();

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

    // --- Coherent triplet pool methods ---

    public void loadCoherentPools(@Nullable Path poolsDir) {
        String[] templateIds = {
            "danger", "sighting", "treasure", "corruption", "conflict",
            "disappearance", "migration", "omen", "weather", "trade",
            "craftsmanship", "community", "nature", "nostalgia", "curiosity", "festival"
        };
        for (String id : templateIds) {
            loadCoherentPool(id, poolsDir);
        }
        LOGGER.atFine().log("Loaded %d coherent pools (%d total entries)",
            coherentPools.size(),
            coherentPools.values().stream().mapToInt(List::size).sum());
    }

    private void loadCoherentPool(String templateId, @Nullable Path poolsDir) {
        String classpathResource = "topics/pools/v2/" + templateId + ".json";
        if (poolsDir != null) {
            Path file = poolsDir.resolve("v2/" + templateId + ".json");
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
            if (obj.has("statCheck")) {
                JsonObject sc = obj.getAsJsonObject("statCheck");
                statCheck = new PoolEntry.StatCheck(sc.get("pass").getAsString(), sc.get("fail").getAsString());
            }
            pool.add(new PoolEntry(id, intro, details, reactions, statCheck));
        }
        coherentPools.put(templateId, pool);
    }

    public List<PoolEntry> getCoherentPool(String templateId) {
        return coherentPools.getOrDefault(templateId, List.of());
    }

    // --- List accessors for shared pools ---

    public List<String> getTimeRefs() { return timeRefs; }
    public List<String> getDirections() { return directions; }
    public List<String> getToneOpeners(String bracket) {
        return toneOpeners.getOrDefault(bracket, List.of());
    }
    public List<String> getToneClosers(String bracket) {
        return toneClosers.getOrDefault(bracket, List.of());
    }
}
