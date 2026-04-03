package com.chonbosmods.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates settlement place names from JSON-driven prefix/suffix pools.
 * Pools are loaded from classpath resources so content can be edited without recompiling.
 */
public final class Nat20PlaceNameGenerator {

    private static final String PREFIX_RESOURCE = "names/place_prefixes.json";
    private static final String SUFFIX_RESOURCE = "names/place_suffixes.json";

    private static List<String> prefixes;
    private static List<String> suffixes;

    private Nat20PlaceNameGenerator() {}

    /**
     * Generate a deterministic place name from a seed.
     * Produces names like "Thornfield", "Ashbrook", "Ironkeep".
     */
    public static String generate(long seed) {
        ensureLoaded();
        Random rng = new Random(seed);
        String prefix = prefixes.get(rng.nextInt(prefixes.size()));
        String suffix = suffixes.get(rng.nextInt(suffixes.size()));
        return prefix + suffix;
    }

    private static synchronized void ensureLoaded() {
        if (prefixes != null) return;
        prefixes = loadPool(PREFIX_RESOURCE);
        suffixes = loadPool(SUFFIX_RESOURCE);
        if (prefixes.isEmpty() || suffixes.isEmpty()) {
            throw new IllegalStateException("Place name pools are empty: check " + PREFIX_RESOURCE + " and " + SUFFIX_RESOURCE);
        }
    }

    private static List<String> loadPool(String resource) {
        List<String> pool = new ArrayList<>();
        try (InputStream is = Nat20PlaceNameGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return pool;
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : arr) pool.add(el.getAsString());
        } catch (Exception e) {
            // Fall through with empty pool: ensureLoaded will throw
        }
        return pool;
    }
}
