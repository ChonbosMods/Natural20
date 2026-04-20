package com.chonbosmods.progression.ambient;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Immutable configuration for the ambient surface-group spawn system. Loaded once at plugin
 * setup from {@code resources/config/ambient_spawn.json}.
 */
public final class AmbientSpawnConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|AmbientCfg");
    private static final String RESOURCE_PATH = "config/ambient_spawn.json";

    private final double rollChance;
    private final long cooldownMillis;
    private final int minDistanceFromPlayer;
    private final int maxDistanceFromPlayer;
    private final int chunkTriggerRadius;
    private final int anchorRetries;
    private final int poiExclusionBlocks;
    private final int settlementExclusionBlocks;
    private final int groupAnchorExclusionBlocks;
    private final long decayWindowMillis;
    private final int decayPlayerNearRadius;
    private final long decaySweepIntervalMillis;

    private AmbientSpawnConfig(double rollChance, long cooldownMillis,
                               int minDistanceFromPlayer, int maxDistanceFromPlayer,
                               int chunkTriggerRadius, int anchorRetries,
                               int poiExclusionBlocks, int settlementExclusionBlocks,
                               int groupAnchorExclusionBlocks,
                               long decayWindowMillis, int decayPlayerNearRadius,
                               long decaySweepIntervalMillis) {
        this.rollChance = rollChance;
        this.cooldownMillis = cooldownMillis;
        this.minDistanceFromPlayer = minDistanceFromPlayer;
        this.maxDistanceFromPlayer = maxDistanceFromPlayer;
        this.chunkTriggerRadius = chunkTriggerRadius;
        this.anchorRetries = anchorRetries;
        this.poiExclusionBlocks = poiExclusionBlocks;
        this.settlementExclusionBlocks = settlementExclusionBlocks;
        this.groupAnchorExclusionBlocks = groupAnchorExclusionBlocks;
        this.decayWindowMillis = decayWindowMillis;
        this.decayPlayerNearRadius = decayPlayerNearRadius;
        this.decaySweepIntervalMillis = decaySweepIntervalMillis;
    }

    public static AmbientSpawnConfig load() {
        try (InputStream in = AmbientSpawnConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     Objects.requireNonNull(in, "missing " + RESOURCE_PATH), StandardCharsets.UTF_8))) {
            JsonObject r = new Gson().fromJson(reader, JsonObject.class);
            AmbientSpawnConfig cfg = new AmbientSpawnConfig(
                    r.get("roll_chance").getAsDouble(),
                    r.get("cooldown_millis").getAsLong(),
                    r.get("min_distance_from_player").getAsInt(),
                    r.get("max_distance_from_player").getAsInt(),
                    r.get("chunk_trigger_radius").getAsInt(),
                    r.get("anchor_retries").getAsInt(),
                    r.get("poi_exclusion_blocks").getAsInt(),
                    r.get("settlement_exclusion_blocks").getAsInt(),
                    r.get("group_anchor_exclusion_blocks").getAsInt(),
                    r.get("decay_window_millis").getAsLong(),
                    r.get("decay_player_near_radius").getAsInt(),
                    r.get("decay_sweep_interval_millis").getAsLong());
            LOGGER.atInfo().log("Loaded ambient_spawn.json: rollChance=%.4f cooldown=%dms",
                    cfg.rollChance, cfg.cooldownMillis);
            return cfg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    public double rollChance()                 { return rollChance; }
    public long cooldownMillis()               { return cooldownMillis; }
    public int minDistanceFromPlayer()         { return minDistanceFromPlayer; }
    public int maxDistanceFromPlayer()         { return maxDistanceFromPlayer; }
    public int chunkTriggerRadius()            { return chunkTriggerRadius; }
    public int anchorRetries()                 { return anchorRetries; }
    public int poiExclusionBlocks()            { return poiExclusionBlocks; }
    public int settlementExclusionBlocks()     { return settlementExclusionBlocks; }
    public int groupAnchorExclusionBlocks()    { return groupAnchorExclusionBlocks; }
    public long decayWindowMillis()            { return decayWindowMillis; }
    public int decayPlayerNearRadius()         { return decayPlayerNearRadius; }
    public long decaySweepIntervalMillis()     { return decaySweepIntervalMillis; }
}
