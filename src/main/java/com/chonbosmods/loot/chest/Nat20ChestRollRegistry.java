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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent position-keyed registry of chests that have already been rolled for
 * Natural 20 loot injection. Every first interaction marks a chest, regardless of
 * whether the roll produced an item, so each chest rolls exactly once.
 *
 * <p>Backed by {@code chest_rolls.json}. Thread-safe: in-memory map is a
 * {@link ConcurrentHashMap}; file I/O is serialized via {@code synchronized}.
 */
public final class Nat20ChestRollRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestRollRegistry");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Long>>() {}.getType();

    private final Path savePath;
    private final ConcurrentHashMap<String, Long> rolled = new ConcurrentHashMap<>();
    private final AtomicBoolean savePending = new AtomicBoolean(false);

    public Nat20ChestRollRegistry(Path rootDir) {
        this.savePath = rootDir.resolve("chest_rolls.json");
    }

    public boolean hasBeenRolled(int x, int y, int z) {
        return rolled.containsKey(key(x, y, z));
    }

    /** Idempotent: repeated calls for the same position overwrite the timestamp but leave the position rolled. */
    public void markRolled(int x, int y, int z) {
        rolled.put(key(x, y, z), System.currentTimeMillis());
        saveAsync();
    }

    /** Synchronous flush. Safe to call from any thread. */
    public synchronized void save() {
        try {
            Files.createDirectories(savePath.getParent());
            try (Writer writer = Files.newBufferedWriter(savePath)) {
                GSON.toJson(rolled, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save chest_rolls.json");
        }
    }

    public synchronized void load() {
        if (!Files.exists(savePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(savePath)) {
            Map<String, Long> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                rolled.putAll(loaded);
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load chest_rolls.json");
        }
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private void saveAsync() {
        if (savePending.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                savePending.set(false);
                save();
            });
        }
    }
}
