package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class Nat20MobAffixRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private final Map<String, Nat20MobAffixDef> mobAffixesById = new HashMap<>();

    public void loadAll(@Nullable Path mobAffixesDir) {
        mobAffixesById.clear();
        if (mobAffixesDir == null || !Files.isDirectory(mobAffixesDir)) return;

        try (Stream<Path> files = Files.list(mobAffixesDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadFile);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to list mob affix directory: %s", mobAffixesDir);
        }
        LOGGER.atInfo().log("Loaded %d mob affix definitions", mobAffixesById.size());
    }

    private void loadFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            String filename = file.getFileName().toString().replace(".json", "");
            String id = "nat20:" + filename;
            mobAffixesById.put(id, parseMobAffix(id, obj));
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse mob affix file: %s", file);
        }
    }

    private Nat20MobAffixDef parseMobAffix(String id, JsonObject obj) {
        Map<String, Double> statMultipliers = new LinkedHashMap<>();
        if (obj.has("StatMultipliers")) {
            JsonObject sm = obj.getAsJsonObject("StatMultipliers");
            for (var entry : sm.entrySet()) {
                statMultipliers.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }

        Map<String, Object> abilityConfig = null;
        if (obj.has("AbilityConfig") && !obj.get("AbilityConfig").isJsonNull()) {
            abilityConfig = new LinkedHashMap<>();
            JsonObject ac = obj.getAsJsonObject("AbilityConfig");
            for (var entry : ac.entrySet()) {
                JsonElement val = entry.getValue();
                if (val.isJsonPrimitive()) {
                    if (val.getAsJsonPrimitive().isBoolean()) {
                        abilityConfig.put(entry.getKey(), val.getAsBoolean());
                    } else if (val.getAsJsonPrimitive().isNumber()) {
                        abilityConfig.put(entry.getKey(), val.getAsDouble());
                    } else {
                        abilityConfig.put(entry.getKey(), val.getAsString());
                    }
                }
            }
        }

        return new Nat20MobAffixDef(
            id,
            obj.has("DisplayName") ? obj.get("DisplayName").getAsString() : id,
            obj.has("Color") ? obj.get("Color").getAsString() : "#ffffff",
            statMultipliers,
            obj.has("AbilityType") ? obj.get("AbilityType").getAsString() : "",
            abilityConfig,
            obj.has("LootBonusMultiplier") ? obj.get("LootBonusMultiplier").getAsDouble() : 1.0,
            obj.has("MinEncounterTier") ? obj.get("MinEncounterTier").getAsInt() : 1
        );
    }

    public Nat20MobAffixDef get(String id) {
        return mobAffixesById.get(id);
    }

    public List<Nat20MobAffixDef> getByMinTier(int maxTier) {
        List<Nat20MobAffixDef> result = new ArrayList<>();
        for (var def : mobAffixesById.values()) {
            if (def.minEncounterTier() <= maxTier) {
                result.add(def);
            }
        }
        return result;
    }

    public int getLoadedCount() {
        return mobAffixesById.size();
    }
}
