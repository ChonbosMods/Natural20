package com.chonbosmods.loot.chest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Nat20ChestLootConfig {

    private static final Gson GSON = new Gson();

    private final List<Double> chancePerBand;
    private final double defaultChance;
    private final Set<String> chestBlockTypes;

    private Nat20ChestLootConfig(List<Double> chancePerBand, double defaultChance, Set<String> chestBlockTypes) {
        this.chancePerBand = List.copyOf(chancePerBand);
        this.defaultChance = defaultChance;
        this.chestBlockTypes = Set.copyOf(chestBlockTypes);
    }

    public static Nat20ChestLootConfig load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            List<Double> bands = new ArrayList<>();
            JsonArray bandArray = root.getAsJsonArray("chance_per_band");
            if (bandArray != null) {
                for (JsonElement e : bandArray) {
                    bands.add(e.getAsDouble());
                }
            }

            double def = root.has("default_chance") ? root.get("default_chance").getAsDouble() : 0.0;

            Set<String> types = new HashSet<>();
            JsonArray typeArray = root.getAsJsonArray("chest_block_types");
            if (typeArray != null) {
                for (JsonElement e : typeArray) {
                    types.add(e.getAsString());
                }
            }

            return new Nat20ChestLootConfig(bands, def, types);
        }
    }

    public double chanceForBand(int band) {
        if (band < 0 || band >= chancePerBand.size()) {
            return defaultChance;
        }
        return chancePerBand.get(band);
    }

    public boolean isChestBlock(String blockTypeName) {
        return chestBlockTypes.contains(blockTypeName);
    }
}
