package com.chonbosmods.progression;

import com.google.gson.Gson;
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
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Nat20MobThemeRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ThemeReg");
    private static final String RESOURCE_PATH = "config/mob_themes.json";

    public record WeightedPool(List<String> roles, double[] cumulativeWeights, double totalWeight) {}

    private final Map<String, WeightedPool> zonePools = new HashMap<>();
    private WeightedPool defaultPool;
    private List<String> outlierDrawPool = List.of();
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

        LOGGER.atInfo().log("Theme registry loaded: %d zones, outlier_pool=%d, outlier_chance=%.2f",
            zonePools.size(), outlierDrawPool.size(), outlierChance);
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
    public String pickMob(@Nullable String zoneName, Random random) {
        if (!initialized) return null;

        // 5% outlier roll: uniform from the combined pool
        if (!outlierDrawPool.isEmpty() && random.nextDouble() < outlierChance) {
            return outlierDrawPool.get(random.nextInt(outlierDrawPool.size()));
        }

        WeightedPool pool = resolvePool(zoneName);
        if (pool == null) return null;

        double pick = random.nextDouble() * pool.totalWeight();
        for (int i = 0; i < pool.cumulativeWeights().length; i++) {
            if (pick < pool.cumulativeWeights()[i]) {
                return pool.roles().get(i);
            }
        }
        return pool.roles().get(pool.roles().size() - 1);
    }

    /** Exact match first, then split on '_' and try the first segment (so Zone1_Tier3 → Zone1).
     *  Falls through to default_biome if neither hits. */
    @Nullable
    private WeightedPool resolvePool(@Nullable String zoneName) {
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
