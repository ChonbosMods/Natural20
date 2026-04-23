package com.chonbosmods.progression;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class Nat20MobThemeRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ThemeReg");
    private static final String RESOURCE_PATH = "config/mob_themes.json";

    public record WeightedPool(List<String> roles, double[] cumulativeWeights, double totalWeight) {}

    /** Ordered biome-keyword entry. First entry whose tokens hit any underscore-split token of
     *  the biome name wins (replace semantics: overrides zone pool entirely). */
    public record BiomeKeyword(Set<String> matchTokens, WeightedPool pool) {}

    private final Map<String, WeightedPool> zonePools = new HashMap<>();
    private final List<BiomeKeyword> biomeKeywords = new ArrayList<>();
    private WeightedPool defaultPool;
    private List<String> outlierDrawPool = List.of();
    private Set<String> poiBlacklist = Set.of();
    private double outlierChance = 0.05;
    private boolean initialized = false;

    public void initialize() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.atWarning().log("mob_themes.json not found on classpath; registry is empty");
                this.initialized = true;
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                loadFrom(root);
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load mob_themes.json");
        }
        this.initialized = true;
    }

    private void loadFrom(JsonObject root) {
        JsonElement oc = root.get("outlier_chance");
        if (oc != null && oc.isJsonPrimitive()) {
            this.outlierChance = Math.max(0.0, Math.min(1.0, oc.getAsDouble()));
        }

        List<String> outlierDraw = new ArrayList<>();
        JsonElement hp = root.get("hostile_pool");
        if (hp != null && hp.isJsonArray()) {
            hp.getAsJsonArray().forEach(e -> outlierDraw.add(e.getAsString()));
        }
        JsonElement oo = root.get("outlier_only");
        if (oo != null && oo.isJsonArray()) {
            oo.getAsJsonArray().forEach(e -> outlierDraw.add(e.getAsString()));
        }
        this.outlierDrawPool = Collections.unmodifiableList(outlierDraw);

        JsonElement bl = root.get("poi_blacklist");
        if (bl != null && bl.isJsonArray()) {
            Set<String> blacklist = new HashSet<>();
            bl.getAsJsonArray().forEach(e -> blacklist.add(e.getAsString()));
            this.poiBlacklist = Collections.unmodifiableSet(blacklist);
        }

        JsonElement db = root.get("default_biome");
        if (db != null && db.isJsonObject()) {
            this.defaultPool = parsePool(db.getAsJsonObject().getAsJsonObject("weights"));
        }

        JsonElement zonesElement = root.has("biomes") ? root.get("biomes") : root.get("zones");
        if (zonesElement != null && zonesElement.isJsonObject()) {
            JsonObject zones = zonesElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : zones.entrySet()) {
                JsonObject weights = entry.getValue().getAsJsonObject().getAsJsonObject("weights");
                WeightedPool pool = parsePool(weights);
                if (pool != null) zonePools.put(entry.getKey(), pool);
            }
        }

        JsonElement kwElement = root.get("biome_keywords");
        if (kwElement != null && kwElement.isJsonArray()) {
            for (JsonElement el : kwElement.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject entry = el.getAsJsonObject();
                JsonArray matchArr = entry.getAsJsonArray("match");
                if (matchArr == null || matchArr.size() == 0) continue;
                Set<String> tokens = new HashSet<>();
                for (JsonElement m : matchArr) tokens.add(m.getAsString());
                WeightedPool pool = parsePool(entry.getAsJsonObject("weights"));
                if (pool != null) {
                    biomeKeywords.add(new BiomeKeyword(Collections.unmodifiableSet(tokens), pool));
                }
            }
        }

        LOGGER.atInfo().log("Theme registry loaded: %d zones, %d biome keywords, outlier_pool=%d, outlier_chance=%.2f, poi_blacklist=%d",
            zonePools.size(), biomeKeywords.size(), outlierDrawPool.size(), outlierChance, poiBlacklist.size());
    }

    @Nullable
    private static WeightedPool parsePool(JsonObject weights) {
        if (weights == null || weights.size() == 0) return null;
        List<String> roles = new ArrayList<>(weights.size());
        List<Double> raws = new ArrayList<>(weights.size());
        double total = 0.0;
        for (Map.Entry<String, JsonElement> entry : weights.entrySet()) {
            double w = entry.getValue().getAsDouble();
            if (w <= 0.0) continue;
            roles.add(entry.getKey());
            raws.add(w);
            total += w;
        }
        if (roles.isEmpty()) return null;
        double[] cumulative = new double[roles.size()];
        double running = 0.0;
        for (int i = 0; i < roles.size(); i++) {
            running += raws.get(i);
            cumulative[i] = running;
        }
        return new WeightedPool(Collections.unmodifiableList(roles), cumulative, total);
    }

    @Nullable
    public String pickMob(@Nullable String zoneName, @Nullable String biomeName, Random random) {
        if (!initialized) return null;

        // 5% outlier roll: uniform from the combined pool
        if (!outlierDrawPool.isEmpty() && random.nextDouble() < outlierChance) {
            return outlierDrawPool.get(random.nextInt(outlierDrawPool.size()));
        }

        WeightedPool pool = resolvePool(zoneName, biomeName);
        if (pool == null) return null;

        double pick = random.nextDouble() * pool.totalWeight();
        for (int i = 0; i < pool.cumulativeWeights().length; i++) {
            if (pick < pool.cumulativeWeights()[i]) {
                return pool.roles().get(i);
            }
        }
        return pool.roles().get(pool.roles().size() - 1);
    }

    /** Like {@link #pickMob} but rejects any role in the POI blacklist (rejection sampling).
     *  Use at POI-style spawn sites (quest encounters, dungeon populations) where mobs whose
     *  behavior (e.g. flee-on-sight) make them unsuitable for fixed-location combat should be
     *  excluded. Ambient spawns keep calling {@link #pickMob} so blacklisted mobs still appear
     *  in the overworld. Returns null if every retry lands on a blacklisted pick; caller should
     *  have its own fallback (QuestGenerator keeps the uniform fallback pick). */
    @Nullable
    public String pickMobForPOI(@Nullable String zoneName, @Nullable String biomeName, Random random) {
        if (poiBlacklist.isEmpty()) return pickMob(zoneName, biomeName, random);
        for (int i = 0; i < 8; i++) {
            String candidate = pickMob(zoneName, biomeName, random);
            if (candidate == null || !poiBlacklist.contains(candidate)) return candidate;
        }
        return null;
    }

    public boolean isPOIBlacklisted(String role) {
        return poiBlacklist.contains(role);
    }

    /** Cascade:
     *  1) biome keyword (first entry whose tokens intersect the biome name's underscore-split tokens)
     *  2) zone exact match
     *  3) zone prefix match (Zone1_Tier3 → Zone1)
     *  4) default_biome
     *  Biome keyword hit uses REPLACE semantics: the keyword pool overrides the zone pool entirely. */
    @Nullable
    private WeightedPool resolvePool(@Nullable String zoneName, @Nullable String biomeName) {
        if (biomeName != null && !biomeKeywords.isEmpty()) {
            String[] tokens = biomeName.split("_");
            for (BiomeKeyword kw : biomeKeywords) {
                for (String tok : tokens) {
                    if (kw.matchTokens().contains(tok)) return kw.pool();
                }
            }
        }
        if (zoneName == null) return defaultPool;
        WeightedPool exact = zonePools.get(zoneName);
        if (exact != null) return exact;
        int underscore = zoneName.indexOf('_');
        if (underscore > 0) {
            WeightedPool prefix = zonePools.get(zoneName.substring(0, underscore));
            if (prefix != null) return prefix;
        }
        return defaultPool;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public List<String> getKnownZones() {
        List<String> names = new ArrayList<>(zonePools.keySet());
        Collections.sort(names);
        return names;
    }

    @Nullable
    public WeightedPool getZonePool(String zone) {
        return zonePools.get(zone);
    }

    public double getOutlierChance() {
        return outlierChance;
    }
}
