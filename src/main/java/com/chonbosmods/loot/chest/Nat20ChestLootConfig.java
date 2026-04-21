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

    private final double chance;
    private final Set<String> chestBlockTypes;

    private Nat20ChestLootConfig(double chance, Set<String> chestBlockTypes) {
        this.chance = chance;
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

        double c = root.has("chance") ? root.get("chance").getAsDouble() : 0.0;

        Set<String> types = new HashSet<>();
        JsonArray typeArray = root.getAsJsonArray("chest_block_types");
        if (typeArray != null) {
            for (JsonElement e : typeArray) {
                types.add(e.getAsString());
            }
        }

        return new Nat20ChestLootConfig(c, types);
    }

    public double getChance() {
        return chance;
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
