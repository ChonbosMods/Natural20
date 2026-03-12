package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.def.GemBonus;
import com.chonbosmods.loot.def.Nat20GemDef;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class Nat20GemRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_DIR = "loot/gems/";
    private static final String[] BUILTIN_FILES = {
        "ruby_of_might.json", "sapphire_of_wisdom.json"
    };

    private final Map<String, Nat20GemDef> gemsById = new HashMap<>();

    public void loadAll(@Nullable Path overrideDir) {
        for (String file : BUILTIN_FILES) {
            loadClasspathFile(CLASSPATH_DIR + file);
        }
        if (overrideDir != null && Files.isDirectory(overrideDir)) {
            try (Stream<Path> files = Files.list(overrideDir)) {
                files.filter(p -> p.toString().endsWith(".json")).forEach(this::loadFile);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to load gem overrides from %s", overrideDir);
            }
        }
        LOGGER.atInfo().log("Loaded %d gem definitions", gemsById.size());
    }

    private void loadClasspathFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            String filename = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
            String id = "nat20:" + filename;
            gemsById.put(id, parseGem(id, obj));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load classpath gem: %s", path);
        }
    }

    private void loadFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            String filename = file.getFileName().toString().replace(".json", "");
            String id = "nat20:" + filename;
            gemsById.put(id, parseGem(id, obj));
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse gem file: %s", file);
        }
    }

    private Nat20GemDef parseGem(String id, JsonObject obj) {
        Stat affinity = obj.has("StatAffinity") ? Stat.valueOf(obj.get("StatAffinity").getAsString()) : null;

        Map<String, Double> purityMult = new LinkedHashMap<>();
        if (obj.has("PurityMultipliers")) {
            JsonObject pm = obj.getAsJsonObject("PurityMultipliers");
            for (var entry : pm.entrySet()) {
                purityMult.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }

        Map<String, GemBonus> bonuses = new LinkedHashMap<>();
        if (obj.has("BonusesBySlot")) {
            JsonObject bs = obj.getAsJsonObject("BonusesBySlot");
            for (var entry : bs.entrySet()) {
                JsonObject bonusObj = entry.getValue().getAsJsonObject();
                bonuses.put(entry.getKey(), new GemBonus(
                    bonusObj.get("Stat").getAsString(),
                    bonusObj.has("Type") ? bonusObj.get("Type").getAsString() : "ADDITIVE",
                    bonusObj.get("BaseValue").getAsDouble()
                ));
            }
        }

        return new Nat20GemDef(
            id,
            obj.has("DisplayName") ? obj.get("DisplayName").getAsString() : id,
            affinity,
            obj.has("AffinityScalingFactor") ? obj.get("AffinityScalingFactor").getAsDouble() : 0.0,
            purityMult,
            bonuses
        );
    }

    public Nat20GemDef get(String id) {
        return gemsById.get(id);
    }

    public int getLoadedCount() {
        return gemsById.size();
    }
}
