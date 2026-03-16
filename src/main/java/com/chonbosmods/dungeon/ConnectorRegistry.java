package com.chonbosmods.dungeon;

import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class ConnectorRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Map<String, ConnectorDef> defsById = new LinkedHashMap<>();

    public void loadAll(Path dir) {
        if (!Files.isDirectory(dir)) {
            LOGGER.atWarning().log("Connector directory does not exist: %s", dir);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json")).sorted().forEach(this::loadFile);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to list connector files in %s", dir);
        }
        LOGGER.atInfo().log("Loaded %d connector definitions", defsById.size());
    }

    private void loadFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            String name = file.getFileName().toString().replace(".json", "");
            ConnectorDef def = parseDef(name, obj);
            registerDef(def);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse connector file: %s", file);
        }
    }

    private ConnectorDef parseDef(String name, JsonObject obj) {
        String prefabKey = obj.get("prefabKey").getAsString();
        double weight = obj.has("weight") ? obj.get("weight").getAsDouble() : 1.0;

        List<String> tags = new ArrayList<>();
        if (obj.has("tags")) {
            for (JsonElement el : obj.getAsJsonArray("tags")) {
                tags.add(el.getAsString());
            }
        }

        return new ConnectorDef(name, prefabKey, tags, weight);
    }

    public void registerDef(ConnectorDef def) {
        defsById.put(def.name(), def);
    }

    public ConnectorDef selectRandom(Random random) {
        double totalWeight = 0;
        for (var def : defsById.values()) {
            totalWeight += def.weight();
        }
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (var def : defsById.values()) {
            cumulative += def.weight();
            if (roll < cumulative) return def;
        }
        // Fallback to last entry
        return defsById.values().iterator().next();
    }

    public ConnectorDef getDef(String name) {
        return defsById.get(name);
    }

    public Collection<ConnectorDef> getAllDefs() {
        return defsById.values();
    }

    public int getDefCount() {
        return defsById.size();
    }
}
