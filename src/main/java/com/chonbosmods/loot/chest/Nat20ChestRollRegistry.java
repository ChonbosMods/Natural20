package com.chonbosmods.loot.chest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent position-keyed registry of chests that have already been rolled for
 * Natural 20 loot injection. Every first interaction marks a chest, regardless of
 * whether the roll produced an item, so each chest rolls exactly once.
 *
 * <p>Backed by {@code chest_rolls.json}. Thread-safe: in-memory map is a
 * {@link ConcurrentHashMap}; file I/O is serialized via {@code synchronized}.
 * {@link #markRolled} writes synchronously so a JVM crash right after a first
 * interaction cannot lose the mark and let the same chest roll again on restart.
 * Chest opens are rare enough (a handful per minute across all players) that the
 * disk I/O cost is negligible; an async debounce buys nothing here.
 */
public final class Nat20ChestRollRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestRollRegistry");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Long>>() {}.getType();

    private volatile Path savePath;
    private final ConcurrentHashMap<String, Long> rolled = new ConcurrentHashMap<>();

    public Nat20ChestRollRegistry() {
    }

    /**
     * Bind the save file to {@code worldDataDir / chest_rolls.json}. Clears in-memory
     * state. Must be called before any save or load; called from the first-chunk-load
     * hook so the registry is scoped to the currently-loaded world.
     */
    public synchronized void setSaveDirectory(Path worldDataDir) {
        this.savePath = worldDataDir.resolve("chest_rolls.json");
        rolled.clear();
    }

    public boolean hasBeenRolled(int x, int y, int z) {
        return rolled.containsKey(key(x, y, z));
    }

    /** Idempotent: repeated calls for the same position overwrite the timestamp but leave the position rolled. */
    public void markRolled(int x, int y, int z) {
        rolled.put(key(x, y, z), System.currentTimeMillis());
        save();
    }

    /**
     * Synchronous flush. Safe to call from any thread.
     *
     * @throws IllegalStateException if {@link #setSaveDirectory} has not been called
     */
    public synchronized void save() {
        if (savePath == null) {
            throw new IllegalStateException("saveDirectory not set; call setSaveDirectory first");
        }
        try {
            Files.createDirectories(savePath.getParent());
            try (Writer writer = Files.newBufferedWriter(savePath)) {
                GSON.toJson(rolled, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save chest_rolls.json");
        }
    }

    /**
     * @throws IllegalStateException if {@link #setSaveDirectory} has not been called
     */
    public synchronized void load() {
        if (savePath == null) {
            throw new IllegalStateException("saveDirectory not set; call setSaveDirectory first");
        }
        if (!Files.exists(savePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(savePath)) {
            Map<String, Long> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                rolled.clear();
                rolled.putAll(loaded);
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load chest_rolls.json");
        }
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
