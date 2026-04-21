package com.chonbosmods.settlement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import com.hypixel.hytale.server.core.universe.world.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory registry of all placed settlements, backed by a JSON file.
 * Thread-safe: uses ConcurrentHashMap and debounced async saves.
 */
public class SettlementRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Settlements");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, SettlementRecord>>() {}.getType();

    private Path savePath;
    private final ConcurrentHashMap<String, SettlementRecord> settlements = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, World> worldCache = new ConcurrentHashMap<>();
    private final AtomicBoolean savePending = new AtomicBoolean(false);

    public SettlementRegistry(Path pluginDataDir) {
        this.savePath = pluginDataDir.resolve("settlements.json");
    }

    /**
     * Rebind the save file to {@code worldDataDir / settlements.json}. Clears
     * in-memory state so the next {@link #load()} reads the world-scoped file
     * fresh. Mirrors {@code CaveVoidRegistry.setSaveFile}.
     */
    public void setSaveDirectory(Path worldDataDir) {
        this.savePath = worldDataDir.resolve("settlements.json");
        settlements.clear();
        worldCache.clear();
    }

    /**
     * Load settlements from the JSON file into memory.
     * If the file does not exist, starts with an empty registry.
     */
    public void load() {
        if (!Files.exists(savePath)) {
            LOGGER.atFine().log("No settlements.json found: starting fresh");
            return;
        }

        try (Reader reader = Files.newBufferedReader(savePath)) {
            Map<String, SettlementRecord> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                settlements.putAll(loaded);
            }
            LOGGER.atFine().log("Loaded " + settlements.size() + " settlement(s) from " + savePath);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load settlements.json");
        }
    }

    /**
     * Register a settlement and trigger an async save.
     */
    public void register(SettlementRecord record) {
        settlements.put(record.getCellKey(), record);
        saveAsync();
    }

    /**
     * Look up a settlement by its cell key.
     * @return the record, or null if not found
     */
    public SettlementRecord getByCell(String cellKey) {
        return settlements.get(cellKey);
    }

    /**
     * Check whether a cell key already has a settlement registered.
     */
    public boolean hasCell(String cellKey) {
        return settlements.containsKey(cellKey);
    }

    /**
     * Find the settlement that contains an NPC with the given UUID.
     * @return the SettlementRecord, or null if no match
     */
    public SettlementRecord getByNpcUUID(UUID uuid) {
        for (SettlementRecord record : settlements.values()) {
            for (NpcRecord npc : record.getNpcs()) {
                if (uuid.equals(npc.getEntityUUID())) {
                    return record;
                }
            }
        }
        return null;
    }

    /**
     * Find an NPC record by entity UUID across all settlements.
     * @return the NpcRecord, or null if no match
     */
    public NpcRecord getNpcByUUID(UUID uuid) {
        for (SettlementRecord record : settlements.values()) {
            for (NpcRecord npc : record.getNpcs()) {
                if (uuid.equals(npc.getEntityUUID())) {
                    return npc;
                }
            }
        }
        return null;
    }

    /**
     * Return the full map for iteration (e.g. by tickers).
     */
    public ConcurrentHashMap<String, SettlementRecord> getAll() {
        return settlements;
    }

    /**
     * @return true if any settlement center is within {@code radius} blocks of {@code (x, z)}
     *         in the XZ plane. Used by ambient spawn anchor validation.
     */
    public boolean isNearAnySettlement(double x, double z, int radius) {
        long r2 = (long) radius * radius;
        for (SettlementRecord s : getAll().values()) {
            double dx = s.getPosX() - x;
            double dz = s.getPosZ() - z;
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    /**
     * Collect all settlement names currently in use across all settlements.
     */
    public java.util.Set<String> getUsedNames() {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (SettlementRecord record : settlements.values()) {
            String name = record.getName();
            if (name != null && !name.isEmpty()) {
                used.add(name);
            }
        }
        return used;
    }

    /**
     * Cache a World reference keyed by its derived UUID
     * (UUID.nameUUIDFromBytes(world.getName().getBytes())).
     * Called by SettlementWorldGenListener when a world is first encountered.
     */
    public void cacheWorld(UUID derivedUUID, World world) {
        worldCache.put(derivedUUID, world);
    }

    /**
     * Retrieve a cached World reference by its derived UUID.
     * @return the World, or null if not cached
     */
    public World getCachedWorld(UUID derivedUUID) {
        return worldCache.get(derivedUUID);
    }

    /**
     * Debounced async save: if no save is already pending, schedule one.
     * The save writes the current snapshot to disk with pretty-printed JSON.
     */
    public void saveAsync() {
        if (savePending.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                savePending.set(false);
                try {
                    Files.createDirectories(savePath.getParent());
                    try (Writer writer = Files.newBufferedWriter(savePath)) {
                        GSON.toJson(settlements, MAP_TYPE, writer);
                    }
                    LOGGER.atFinest().log("Saved %d settlement(s)", settlements.size());
                } catch (IOException e) {
                    LOGGER.atSevere().withCause(e).log("Failed to save settlements.json");
                }
            });
        }
    }
}
