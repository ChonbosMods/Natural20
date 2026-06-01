package com.chonbosmods.loot.chest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks which native world-generated loot chests are eligible (and not yet looted) for
 * Nat20 affix injection. Populated cheaply at chunk generation by
 * {@link Nat20ChestEligibilityStampSystem} (when a chest's engine {@code droplist} is set,
 * i.e. it is a genuine worldgen loot chest, before StashSystem consumes it). Consumed by
 * {@link Nat20ChestOpenInjectionSystem} on first open, which mints + injects loot on demand.
 *
 * <p>Storing eligibility this way keeps minting on-demand (only opened chests mint, so no
 * AssetUpdate traffic for chests that are generated but never opened) while preserving the
 * worldgen-only gate: player-placed and broken-and-replaced chests are never stamped, so
 * they never receive loot. Position-keyed and persisted so eligibility survives chunk
 * unload/reload (which fires as {@code LOAD}, not {@code SPAWN}, and so does not re-stamp).
 *
 * <p>A stamped-but-never-opened chest leaves a lightweight string entry until looted; an
 * entry whose chest is destroyed before opening is a harmless stale key (no chest there to
 * open). Saves are debounced off the world thread.
 */
public final class Nat20ChestEligibilityRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestEligibility");
    private static final Gson GSON = new Gson();
    private static final long SAVE_DEBOUNCE_MS = 2000L;

    private final Set<String> eligible = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private Path saveFile;

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public void setSaveDirectory(Path worldDataDir) {
        this.saveFile = worldDataDir.resolve("chest_eligibility.json");
        eligible.clear();
    }

    public void markEligible(int x, int y, int z) {
        if (eligible.add(key(x, y, z))) scheduleSave();
    }

    public boolean isEligible(int x, int y, int z) {
        return eligible.contains(key(x, y, z));
    }

    /** Consume eligibility once the chest has been opened + rolled. Idempotent. */
    public void markLooted(int x, int y, int z) {
        if (eligible.remove(key(x, y, z))) scheduleSave();
    }

    public int size() {
        return eligible.size();
    }

    private void scheduleSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(this::save, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void save() {
        saveScheduled.set(false);
        if (saveFile == null) return;
        try {
            Files.createDirectories(saveFile.getParent());
            Files.writeString(saveFile, GSON.toJson(new HashSet<>(eligible)));
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save chest eligibility registry");
        }
    }

    public void load() {
        if (saveFile == null || !Files.exists(saveFile)) return;
        try {
            String json = Files.readString(saveFile);
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                eligible.addAll(loaded);
                LOGGER.atInfo().log("Loaded %d eligible chest positions", loaded.size());
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load chest eligibility registry");
        }
    }
}
