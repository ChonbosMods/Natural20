package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.EquipmentCategory;
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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Nat20LootEntryRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_DIR = "loot/entries/";
    private static final String[] BUILTIN_FILES = {"vanilla.json"};

    private final Map<String, String> manualTags = new HashMap<>();

    public void loadAll(@Nullable Path overrideDir) {
        for (String file : BUILTIN_FILES) {
            loadClasspathFile(CLASSPATH_DIR + file);
        }
        if (overrideDir != null && Files.isDirectory(overrideDir)) {
            try (Stream<Path> files = Files.list(overrideDir)) {
                files.filter(p -> p.toString().endsWith(".json")).forEach(this::loadFile);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to load loot entry overrides from %s", overrideDir);
            }
        }
        LOGGER.atInfo().log("Loaded %d manual loot entry tags", manualTags.size());
    }

    private void loadClasspathFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseEntries(obj);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load classpath loot entries: %s", path);
        }
    }

    private void loadFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            parseEntries(obj);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse loot entry file: %s", file);
        }
    }

    private void parseEntries(JsonObject root) {
        if (!root.has("Items")) return;
        JsonObject items = root.getAsJsonObject("Items");
        for (var entry : items.entrySet()) {
            String itemId = entry.getKey();
            JsonObject itemObj = entry.getValue().getAsJsonObject();
            String category = itemObj.get("Category").getAsString();
            manualTags.put(itemId, category);
        }
    }

    @Nullable
    public EquipmentCategory getManualCategory(String itemId) {
        String key = manualTags.get(itemId);
        return key != null ? EquipmentCategory.fromKey(key) : null;
    }

    @Nullable
    public String getManualCategoryKey(String itemId) {
        return manualTags.get(itemId);
    }

    public int getLoadedCount() {
        return manualTags.size();
    }
}
