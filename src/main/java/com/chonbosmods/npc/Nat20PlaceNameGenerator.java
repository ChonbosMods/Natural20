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
import java.util.Set;

/**
 * Generates unique settlement place names from a JSON pool of curated names.
 * Names are never repeated within a world save until all names are exhausted.
 */
public final class Nat20PlaceNameGenerator {

    private static final String NAMES_RESOURCE = "names/place_names.json";

    private static List<String> allNames;

    private Nat20PlaceNameGenerator() {}

    /**
     * Pick a name that hasn't been used yet. If all names are exhausted,
     * the full pool resets and names may repeat.
     *
     * @param seed      deterministic seed for selection
     * @param usedNames names already assigned to settlements in this world
     * @return a name not in usedNames (unless pool is exhausted)
     */
    public static String generate(long seed, Set<String> usedNames) {
        ensureLoaded();
        List<String> available = new ArrayList<>(allNames.size());
        for (String name : allNames) {
            if (!usedNames.contains(name)) {
                available.add(name);
            }
        }
        if (available.isEmpty()) {
            // All 1000+ names used: reset and pick from full pool
            available = allNames;
        }
        Random rng = new Random(seed);
        return available.get(rng.nextInt(available.size()));
    }

    /**
     * Fallback for legacy records with no stored name: deterministic but without
     * uniqueness guarantees (no knowledge of what's been used).
     */
    public static String generate(long seed) {
        ensureLoaded();
        Random rng = new Random(seed);
        return allNames.get(rng.nextInt(allNames.size()));
    }

    public static int poolSize() {
        ensureLoaded();
        return allNames.size();
    }

    private static synchronized void ensureLoaded() {
        if (allNames != null) return;
        allNames = new ArrayList<>();
        try (InputStream is = Nat20PlaceNameGenerator.class.getClassLoader().getResourceAsStream(NAMES_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Place name pool not found: " + NAMES_RESOURCE);
            }
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : arr) allNames.add(el.getAsString());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load place name pool: " + NAMES_RESOURCE, e);
        }
        if (allNames.isEmpty()) {
            throw new IllegalStateException("Place name pool is empty: " + NAMES_RESOURCE);
        }
    }
}
