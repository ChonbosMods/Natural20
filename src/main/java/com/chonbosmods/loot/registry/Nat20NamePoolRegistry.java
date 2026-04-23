package com.chonbosmods.loot.registry;

import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads tiered name pools from loot/names/*.json and provides
 * random name lookup by affix ID and rarity tier.
 */
public class Nat20NamePoolRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_DIR = "loot/names/";

    /** affix ID (without "nat20:" prefix) -> rarity (lowercase) -> list of names */
    private final Map<String, Map<String, List<String>>> poolsByAffixId = new HashMap<>();

    public void loadAll() {
        String[] files = {
            "arm_evasion.json", "arm_fire_res.json",
            "arm_frost_res.json", "arm_gallant.json",
            "arm_lightfoot.json", "arm_poison_res.json",
            "arm_resilience.json", "arm_thorns.json", "arm_void_res.json",
            "arm_waterbreath.json",
            "wpn_backstab.json", "wpn_crush.json", "wpn_fear.json",
            "wpn_fire_dot.json", "wpn_fire_flat.json", "wpn_fire_weak.json",
            "wpn_frost_dot.json", "wpn_frost_flat.json", "wpn_frost_weak.json",
            "wpn_hex.json", "wpn_lifeleech.json", "wpn_manaleech.json",
            "wpn_mockery.json", "wpn_poison_dot.json", "wpn_poison_flat.json",
            "wpn_poison_weak.json", "wpn_rally.json", "wpn_void_dot.json",
            "wpn_void_flat.json", "wpn_void_weak.json",
            "stat_score_str.json", "stat_score_dex.json", "stat_score_con.json",
            "stat_score_int.json", "stat_score_wis.json", "stat_score_cha.json"
        };
        for (String file : files) {
            loadFile(CLASSPATH_DIR + file);
        }
        LOGGER.atInfo().log("Loaded %d name pools", poolsByAffixId.size());
    }

    private void loadFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.atWarning().log("Name pool file not found: %s", path);
                return;
            }
            JsonObject obj = JsonParser.parseReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            String affixId = obj.get("affix_id").getAsString();
            JsonObject tiers = obj.getAsJsonObject("tiers");

            Map<String, List<String>> tierMap = new HashMap<>();
            for (var entry : tiers.entrySet()) {
                List<String> names = new ArrayList<>();
                JsonArray arr = entry.getValue().getAsJsonArray();
                for (var el : arr) {
                    names.add(el.getAsString());
                }
                tierMap.put(entry.getKey(), names);
            }
            poolsByAffixId.put(affixId, tierMap);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load name pool: %s", path);
        }
    }

    /**
     * Get a random tiered name for an affix at a given rarity.
     *
     * @param affixId the affix ID without "nat20:" prefix (e.g., "fire_resistance")
     * @param rarityId the rarity name (e.g., "Rare") - lowercased internally for lookup
     * @param random the random source
     * @return a tiered name, or null if no pool exists for this affix/rarity
     */
    public String getRandomName(String affixId, String rarityId, Random random) {
        Map<String, List<String>> tierMap = poolsByAffixId.get(affixId);
        if (tierMap == null) return null;
        List<String> names = tierMap.get(rarityId.toLowerCase());
        if (names == null || names.isEmpty()) return null;
        return names.get(random.nextInt(names.size()));
    }

    public int getLoadedCount() {
        return poolsByAffixId.size();
    }
}
