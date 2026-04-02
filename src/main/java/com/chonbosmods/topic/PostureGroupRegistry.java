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

public class PostureGroupRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_RESOURCE = "topics/pools/posture_groups.json";

    private final Map<String, PostureGroup> groups = new LinkedHashMap<>();

    public void loadAll(@Nullable Path poolsDir) {
        loadFromClasspath();
        if (poolsDir != null) {
            Path file = poolsDir.resolve("posture_groups.json");
            if (Files.exists(file)) loadFromFile(file);
        }
        LOGGER.atFine().log("Loaded %d posture groups", groups.size());
    }

    private void loadFromClasspath() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (is == null) return;
            parseJson(JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load posture groups from classpath");
        }
    }

    private void loadFromFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            groups.clear();
            parseJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load posture groups: %s", file);
        }
    }

    private void parseJson(JsonObject root) {
        JsonObject pg = root.getAsJsonObject("posture_groups");
        if (pg == null) return;
        for (var entry : pg.entrySet()) {
            String name = entry.getKey();
            JsonObject obj = entry.getValue().getAsJsonObject();

            int warmth = obj.get("warmth").getAsInt();
            int trust = obj.get("trust").getAsInt();
            int dispositionModifier = obj.get("disposition_modifier").getAsInt();

            Set<String> valenceAffinity = new LinkedHashSet<>();
            for (JsonElement el : obj.getAsJsonArray("valence_affinity")) {
                valenceAffinity.add(el.getAsString());
            }

            Map<String, List<String>> prompts = new LinkedHashMap<>();
            JsonElement promptsEl = obj.get("prompts");
            if (promptsEl.isJsonObject()) {
                // Nested valence lanes format
                for (var lane : promptsEl.getAsJsonObject().entrySet()) {
                    List<String> entries = new ArrayList<>();
                    for (JsonElement el : lane.getValue().getAsJsonArray()) {
                        entries.add(el.getAsString());
                    }
                    prompts.put(lane.getKey(), entries);
                }
            } else if (promptsEl.isJsonArray()) {
                // Legacy flat array: treat all as neutral
                List<String> entries = new ArrayList<>();
                for (JsonElement el : promptsEl.getAsJsonArray()) {
                    entries.add(el.getAsString());
                }
                prompts.put("neutral", entries);
            }

            groups.put(name, new PostureGroup(name, warmth, trust, valenceAffinity, dispositionModifier, prompts));
        }
    }

    public PostureGroup getGroup(String name) {
        return groups.get(name);
    }

    public Map<String, PostureGroup> getAllGroups() {
        return Collections.unmodifiableMap(groups);
    }
}
