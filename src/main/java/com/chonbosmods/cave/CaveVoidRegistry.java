package com.chonbosmods.cave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private Path savePath;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    // Serialises save execution so two ForkJoinPool workers cannot open
    // savePath for write at the same time. Concurrent BufferedWriters with
    // independent file offsets interleave bytes and produce malformed JSON.
    private final Object saveLock = new Object();

    public CaveVoidRegistry() {
    }

    /**
     * Bind the save file. Clears in-memory state. Must be called before any save or
     * load; called from the first-chunk-load hook so the registry is scoped to the
     * currently-loaded world.
     */
    public void setSaveFile(Path newSavePath) {
        this.savePath = newSavePath;
        voidsByCell.clear();
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
     * Find the single closest unclaimed void within the given range band.
     * @return the closest unclaimed CaveVoidRecord in range, or null if none qualify
     */
    public @Nullable CaveVoidRecord findNearbyVoid(double x, double z, int minRange, int maxRange) {
        int px = (int) x;
        int pz = (int) z;
        CaveVoidRecord best = null;
        int bestDist = Integer.MAX_VALUE;

        for (List<CaveVoidRecord> cell : voidsByCell.values()) {
            synchronized (cell) {
                for (CaveVoidRecord v : cell) {
                    if (v.isClaimed()) continue;
                    int dist = v.distanceTo(px, pz);
                    if (dist >= minRange && dist <= maxRange && dist < bestDist) {
                        bestDist = dist;
                        best = v;
                    }
                }
            }
        }
        return best;
    }

    /**
     * @return true if any void (claimed or unclaimed) lies within {@code radius} blocks
     *         of {@code (x, z)} in the XZ plane. Used by ambient spawn anchor validation
     *         to keep ambient groups clear of POI quest anchors.
     */
    public boolean isNearAnyVoid(double x, double z, int radius) {
        int px = (int) x;
        int pz = (int) z;
        for (List<CaveVoidRecord> cell : voidsByCell.values()) {
            synchronized (cell) {
                for (CaveVoidRecord v : cell) {
                    if (v.distanceTo(px, pz) <= radius) return true;
                }
            }
        }
        return false;
    }

    /**
     * Find a void by its exact center coordinates, regardless of claim status.
     * Used to look up pre-claimed voids at placement time.
     */
    public @Nullable CaveVoidRecord findVoidAt(int cx, int cy, int cz) {
        for (List<CaveVoidRecord> cell : voidsByCell.values()) {
            synchronized (cell) {
                for (CaveVoidRecord v : cell) {
                    if (v.getCenterX() == cx && v.getCenterY() == cy && v.getCenterZ() == cz) {
                        return v;
                    }
                }
            }
        }
        return null;
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
     * If the file is malformed, moves it aside as a .corrupt-<ts> backup and
     * starts fresh so plugin startup is not blocked.
     */
    public void load() {
        if (savePath == null) {
            throw new IllegalStateException("saveFile not set; call setSaveFile first");
        }
        if (!Files.exists(savePath)) {
            LOGGER.atFine().log("No cave_voids.json found: starting fresh");
            return;
        }

        try (Reader reader = Files.newBufferedReader(savePath)) {
            Map<String, List<CaveVoidRecord>> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                voidsByCell.putAll(loaded);
            }
            LOGGER.atFine().log("Loaded " + getCount() + " cave void(s) from " + savePath);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load cave_voids.json");
        } catch (JsonSyntaxException | IllegalStateException e) {
            voidsByCell.clear();
            Path backup = savePath.resolveSibling(
                    savePath.getFileName().toString() + ".corrupt-" + Instant.now().toEpochMilli());
            try {
                Files.move(savePath, backup, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.atSevere().withCause(e).log(
                        "cave_voids.json is malformed; moved to " + backup + " and starting fresh");
            } catch (IOException ioe) {
                LOGGER.atSevere().withCause(ioe).log(
                        "cave_voids.json is malformed and could not be backed up; starting fresh");
            }
        }
    }

    /**
     * Debounced async save: if no save is already pending, schedule one.
     * Writes a snapshot of the registry to disk as pretty-printed JSON.
     *
     * <p>The snapshot is captured under the same per-cell locks used by
     * {@link #register}, so Gson never iterates a list while a scanner thread
     * is concurrently mutating it. The write itself runs inside
     * {@link #saveLock} so queued saves execute serially and cannot truncate
     * the file out from under each other.
     */
    public CompletableFuture<Void> saveAsync() {
        if (savePath == null) {
            throw new IllegalStateException("saveFile not set; call setSaveFile first");
        }
        if (dirty.compareAndSet(true, false)) {
            return CompletableFuture.runAsync(() -> {
                synchronized (saveLock) {
                    Map<String, List<CaveVoidRecord>> snapshot = snapshotUnderLocks();
                    try {
                        Files.createDirectories(savePath.getParent());
                        try (Writer writer = Files.newBufferedWriter(savePath)) {
                            GSON.toJson(snapshot, MAP_TYPE, writer);
                        }
                        LOGGER.atFine().log("Saved " + getCount() + " cave void(s) to " + savePath);
                    } catch (IOException e) {
                        dirty.set(true);
                        LOGGER.atSevere().withCause(e).log("Failed to save cave_voids.json");
                    }
                }
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Deep-copy every cell list and record under the per-cell locks. The
     * returned map is a caller-owned view safe to hand to Gson.
     */
    private Map<String, List<CaveVoidRecord>> snapshotUnderLocks() {
        Map<String, List<CaveVoidRecord>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<CaveVoidRecord>> entry : voidsByCell.entrySet()) {
            List<CaveVoidRecord> cell = entry.getValue();
            List<CaveVoidRecord> copies;
            synchronized (cell) {
                copies = new ArrayList<>(cell.size());
                for (CaveVoidRecord r : cell) {
                    copies.add(deepCopy(r));
                }
            }
            snapshot.put(entry.getKey(), copies);
        }
        return snapshot;
    }

    private static CaveVoidRecord deepCopy(CaveVoidRecord src) {
        List<int[]> floors = src.getFloorPositions();
        List<int[]> floorsCopy;
        if (floors == null) {
            floorsCopy = null;
        } else {
            floorsCopy = new ArrayList<>(floors.size());
            for (int[] p : floors) {
                floorsCopy.add(p == null ? null : p.clone());
            }
        }
        CaveVoidRecord copy = new CaveVoidRecord(
                src.getCenterX(), src.getCenterY(), src.getCenterZ(),
                src.getMinX(), src.getMinY(), src.getMinZ(),
                src.getMaxX(), src.getMaxY(), src.getMaxZ(),
                src.getVolume(), floorsCopy, src.getChunkKey()
        );
        if (src.isClaimed()) {
            copy.claim(src.getClaimedBySettlement());
        }
        return copy;
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
