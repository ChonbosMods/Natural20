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
 * Generates settlement place names from a JSON pool of curated names.
 * Pool is loaded from classpath so content can be edited without recompiling.
 */
public final class Nat20PlaceNameGenerator {

    private static final String NAMES_RESOURCE = "names/place_names.json";

    private static List<String> names;

    private Nat20PlaceNameGenerator() {}

    /**
     * Pick a deterministic place name from the pool using a seed.
     */
    public static String generate(long seed) {
        ensureLoaded();
        Random rng = new Random(seed);
        return names.get(rng.nextInt(names.size()));
    }

    private static synchronized void ensureLoaded() {
        if (names != null) return;
        names = new ArrayList<>();
        try (InputStream is = Nat20PlaceNameGenerator.class.getClassLoader().getResourceAsStream(NAMES_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Place name pool not found: " + NAMES_RESOURCE);
            }
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : arr) names.add(el.getAsString());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load place name pool: " + NAMES_RESOURCE, e);
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("Place name pool is empty: " + NAMES_RESOURCE);
        }
    }
}
