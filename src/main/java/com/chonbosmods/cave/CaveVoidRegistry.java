package com.chonbosmods.cave;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe registry for discovered cave voids, backed by a JSON file.
 * Uses ConcurrentHashMap keyed by spatial cell and debounced async saves.
 */
public class CaveVoidRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|CaveVoids");
    private static final int CELL_SIZE = 512;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<CaveVoidRecord>>>() {}.getType();

    private final ConcurrentHashMap<String, List<CaveVoidRecord>> voidsByCell = new ConcurrentHashMap<>();
    private final Path savePath;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public CaveVoidRegistry(Path savePath) {
        this.savePath = savePath;
    }

    /**
     * Register a cave void. Deduplicates by merging if an existing void's center
     * is within 64 blocks (horizontal distance).
     */
    public void register(CaveVoidRecord record) {
        String key = cellKey(record.getCenterX(), record.getCenterZ());
        List<CaveVoidRecord> list = voidsByCell.computeIfAbsent(key, k -> new ArrayList<>());

        synchronized (list) {
            for (CaveVoidRecord existing : list) {
                int dx = existing.getCenterX() - record.getCenterX();
                int dz = existing.getCenterZ() - record.getCenterZ();
                if (dx * dx + dz * dz < 64 * 64) {
                    existing.merge(record);
                    dirty.set(true);
                    return;
                }
            }
            list.add(record);
        }

        dirty.set(true);
        saveAsync();
    }

    /**
     * Find unclaimed voids within the given range band, sorted by distance ascending.
     */
    public List<CaveVoidRecord> findNearbyVoids(int posX, int posZ, int minRange, int maxRange) {
        List<CaveVoidRecord> result = new ArrayList<>();

        for (List<CaveVoidRecord> cell : voidsByCell.values()) {
            synchronized (cell) {
                for (CaveVoidRecord v : cell) {
                    if (v.isClaimed()) continue;
                    int dist = v.distanceTo(posX, posZ);
                    if (dist >= minRange && dist <= maxRange) {
                        result.add(v);
                    }
                }
            }
        }

        result.sort(Comparator.comparingInt(v -> v.distanceTo(posX, posZ)));
        return result;
    }

    /**
     * Find the closest unclaimed void, with no range filter.
     * @return the closest unclaimed CaveVoidRecord, or null if none exist
     */
    public CaveVoidRecord findAnyVoid(int posX, int posZ) {
        CaveVoidRecord closest = null;
        int closestDist = Integer.MAX_VALUE;

        for (List<CaveVoidRecord> cell : voidsByCell.values()) {
            synchronized (cell) {
                for (CaveVoidRecord v : cell) {
                    if (v.isClaimed()) continue;
                    int dist = v.distanceTo(posX, posZ);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = v;
                    }
                }
            }
        }

        return closest;
    }

    /**
     * Claim a void for a settlement, mark dirty, and trigger an async save.
     */
    public void claimVoid(CaveVoidRecord record, String settlementCellKey) {
        record.claim(settlementCellKey);
        dirty.set(true);
        saveAsync();
    }

    /**
     * Load voids from the JSON file into memory.
     * If the file does not exist, starts with an empty registry.
     */
    public void load() {
        if (!Files.exists(savePath)) {
            LOGGER.atInfo().log("No cave_voids.json found: starting fresh");
            return;
        }

        try (Reader reader = Files.newBufferedReader(savePath)) {
            Map<String, List<CaveVoidRecord>> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                voidsByCell.putAll(loaded);
            }
            LOGGER.atInfo().log("Loaded " + getCount() + " cave void(s) from " + savePath);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load cave_voids.json");
        }
    }

    /**
     * Debounced async save: if no save is already pending, schedule one.
     * Writes the current snapshot to disk with pretty-printed JSON.
     */
    public CompletableFuture<Void> saveAsync() {
        if (dirty.compareAndSet(true, false)) {
            return CompletableFuture.runAsync(() -> {
                try {
                    Files.createDirectories(savePath.getParent());
                    try (Writer writer = Files.newBufferedWriter(savePath)) {
                        GSON.toJson(voidsByCell, MAP_TYPE, writer);
                    }
                    LOGGER.atInfo().log("Saved " + getCount() + " cave void(s) to " + savePath);
                } catch (IOException e) {
                    LOGGER.atSevere().withCause(e).log("Failed to save cave_voids.json");
                }
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Wipe all voids, mark dirty, and trigger an async save.
     */
    public void clear() {
        voidsByCell.clear();
        dirty.set(true);
        saveAsync();
    }

    /**
     * Return a flat list of all voids across all cells.
     */
    public List<CaveVoidRecord> getAll() {
        List<CaveVoidRecord> all = new ArrayList<>();
        for (List<CaveVoidRecord> cell : voidsByCell.values()) {
            synchronized (cell) {
                all.addAll(cell);
            }
        }
        return all;
    }

    /**
     * Return the total count of registered voids.
     */
    public int getCount() {
        int count = 0;
        for (List<CaveVoidRecord> cell : voidsByCell.values()) {
            synchronized (cell) {
                count += cell.size();
            }
        }
        return count;
    }

    /**
     * Compute the cell key for a world position.
     */
    public static String cellKey(int x, int z) {
        return Math.floorDiv(x, CELL_SIZE) + "," + Math.floorDiv(z, CELL_SIZE);
    }
}
