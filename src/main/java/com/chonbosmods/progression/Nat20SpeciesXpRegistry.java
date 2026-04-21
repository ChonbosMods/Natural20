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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code config/species_xp_weights.json} at boot and resolves role-name
 * to a multiplier. Used by {@link Nat20XpOnKillSystem} to scale XP rewards by
 * species threat on top of the tier weight. Unknown roles default to 1.0 so
 * a newly-added mob never zeroes out.
 */
public final class Nat20SpeciesXpRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|SpeciesXp");
    private static final String RESOURCE_PATH = "config/species_xp_weights.json";
    private static final double DEFAULT_WEIGHT = 1.0;

    private Map<String, Double> weights = Collections.emptyMap();
    private boolean initialized = false;

    public void initialize() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.atWarning().log("%s not found on classpath; all roles default to %.1f",
                    RESOURCE_PATH, DEFAULT_WEIGHT);
                this.initialized = true;
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                loadFrom(root);
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load %s", RESOURCE_PATH);
        }
        this.initialized = true;
    }

    private void loadFrom(JsonObject root) {
        JsonElement weightsElement = root.get("weights");
        if (weightsElement == null || !weightsElement.isJsonObject()) {
            LOGGER.atWarning().log("species_xp_weights.json missing 'weights' object");
            return;
        }
        Map<String, Double> parsed = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : weightsElement.getAsJsonObject().entrySet()) {
            JsonElement v = entry.getValue();
            if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) continue;
            double w = v.getAsDouble();
            if (w < 0.0) continue;
            parsed.put(entry.getKey(), w);
        }
        this.weights = Collections.unmodifiableMap(parsed);
        LOGGER.atInfo().log("Species XP registry loaded: %d entries", weights.size());
    }

    /** @return the species multiplier for {@code roleName}, or {@link #DEFAULT_WEIGHT} (1.0)
     *  if the role is missing from the registry. Never returns null. */
    public double getWeight(@Nullable String roleName) {
        if (roleName == null) return DEFAULT_WEIGHT;
        Double w = weights.get(roleName);
        return w != null ? w : DEFAULT_WEIGHT;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int size() {
        return weights.size();
    }
}
