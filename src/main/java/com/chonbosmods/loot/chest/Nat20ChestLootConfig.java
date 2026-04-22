package com.chonbosmods.loot.chest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class Nat20ChestLootConfig {

    private static final Gson GSON = new Gson();
    private static final String RESOURCE_PATH = "config/chest_loot.json";

    private final double primaryChance;
    private final double secondaryChance;
    private final double secondaryLowRarityBias;
    private final Set<String> chestBlockTypes;

    private Nat20ChestLootConfig(double primaryChance,
                                 double secondaryChance,
                                 double secondaryLowRarityBias,
                                 Set<String> chestBlockTypes) {
        this.primaryChance = primaryChance;
        this.secondaryChance = secondaryChance;
        this.secondaryLowRarityBias = secondaryLowRarityBias;
        this.chestBlockTypes = Set.copyOf(chestBlockTypes);
    }

    public static Nat20ChestLootConfig load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return parse(reader);
        }
    }

    public static Nat20ChestLootConfig load() {
        try (InputStream in = Nat20ChestLootConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     Objects.requireNonNull(in, "missing " + RESOURCE_PATH), StandardCharsets.UTF_8))) {
            return parse(reader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    private static Nat20ChestLootConfig parse(Reader reader) {
        JsonObject root = GSON.fromJson(reader, JsonObject.class);

        double primary = root.has("primary_chance") ? root.get("primary_chance").getAsDouble() : 0.0;
        double secondary = root.has("secondary_chance") ? root.get("secondary_chance").getAsDouble() : 0.0;
        double lowBias = root.has("secondary_low_rarity_bias")
                ? root.get("secondary_low_rarity_bias").getAsDouble()
                : 0.0;

        Set<String> types = new HashSet<>();
        JsonArray typeArray = root.getAsJsonArray("chest_block_types");
        if (typeArray != null) {
            for (JsonElement e : typeArray) {
                types.add(e.getAsString());
            }
        }

        return new Nat20ChestLootConfig(primary, secondary, lowBias, types);
    }

    public double getPrimaryChance() {
        return primaryChance;
    }

    /** Conditional chance of a second item, gated on the primary roll succeeding. */
    public double getSecondaryChance() {
        return secondaryChance;
    }

    /**
     * Probability (0..1) that the secondary item's rarity is clamped to Uncommon
     * maximum. When it misses, the secondary uses the full ilvl gate. A soft bias
     * rather than a hard cap: raises Common/Uncommon frequency and reduces
     * Rare/Epic/Legendary without eliminating them. Default 0.7.
     */
    public double getSecondaryLowRarityBias() {
        return secondaryLowRarityBias;
    }

    public boolean isChestBlock(String blockTypeName) {
        if (blockTypeName == null) return false;
        return chestBlockTypes.contains(normalizeBlockTypeId(blockTypeName));
    }

    /**
     * Runtime {@code BlockType.getId()} returns state-machine variants like
     * {@code *Furniture_Kweebec_Chest_Small_State_Definitions_CloseWindow}. Strip the
     * leading {@code *} markers and the {@code _State_Definitions_<state>} suffix so
     * the allowlist can store base names (e.g. {@code Furniture_Kweebec_Chest_Small}).
     */
    static String normalizeBlockTypeId(String id) {
        int start = 0;
        while (start < id.length() && id.charAt(start) == '*') start++;
        String stripped = start == 0 ? id : id.substring(start);
        int suffix = stripped.indexOf("_State_Definitions_");
        return suffix >= 0 ? stripped.substring(0, suffix) : stripped;
    }

    public int blockTypeCount() {
        return chestBlockTypes.size();
    }
}
