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

public class PromptGroupRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_RESOURCE = "topics/pools/prompt_groups.json";

    private final Map<String, List<String>> groups = new LinkedHashMap<>();

    public void loadAll(@Nullable Path poolsDir) {
        loadFromClasspath();
        if (poolsDir != null) {
            Path file = poolsDir.resolve("prompt_groups.json");
            if (Files.exists(file)) loadFromFile(file);
        }
        LOGGER.atFine().log("Loaded %d prompt groups", groups.size());
    }

    private void loadFromClasspath() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (is == null) return;
            parseJson(JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load prompt groups from classpath");
        }
    }

    private void loadFromFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            groups.clear();
            parseJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load prompt groups: %s", file);
        }
    }

    private void parseJson(JsonObject root) {
        JsonObject pg = root.getAsJsonObject("prompt_groups");
        if (pg == null) return;
        for (var entry : pg.entrySet()) {
            List<String> prompts = new ArrayList<>();
            for (JsonElement el : entry.getValue().getAsJsonArray()) {
                prompts.add(el.getAsString());
            }
            groups.put(entry.getKey(), prompts);
        }
    }

    public String draw(String groupName, PercentageDedup dedup, Random random) {
        List<String> pool = groups.getOrDefault(groupName, List.of());
        if (pool.isEmpty()) return "Tell me more.";
        int idx = dedup.draw("prompt_" + groupName, pool.size(), random);
        return pool.get(idx);
    }

    public String random(String groupName, Random random) {
        List<String> pool = groups.getOrDefault(groupName, List.of());
        if (pool.isEmpty()) return "Tell me more.";
        return pool.get(random.nextInt(pool.size()));
    }

    public List<String> getGroup(String groupName) {
        return groups.getOrDefault(groupName, List.of());
    }
}
