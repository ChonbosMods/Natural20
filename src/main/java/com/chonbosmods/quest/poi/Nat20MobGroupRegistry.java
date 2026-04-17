package com.chonbosmods.quest.poi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory registry of all active POI mob groups, backed by {@code mob_groups.json}.
 * Thread-safe: uses {@link ConcurrentHashMap} and debounced async saves, mirroring
 * {@link com.chonbosmods.settlement.SettlementRegistry}.
 *
 * <p>Keyed by {@link MobGroupRecord#getGroupKey()}
 * ({@code poi:{playerUuid}:{questId}:{poiSlotIdx}}). One record per (player, quest, POI slot).
 */
public class Nat20MobGroupRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobGroups");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, MobGroupRecord>>() {}.getType();

    private final Path savePath;
    private final ConcurrentHashMap<String, MobGroupRecord> groups = new ConcurrentHashMap<>();
    private final AtomicBoolean savePending = new AtomicBoolean(false);

    public Nat20MobGroupRegistry(Path pluginDataDir) {
        this.savePath = pluginDataDir.resolve("mob_groups.json");
    }

    /**
     * Load groups from {@code mob_groups.json} if it exists.
     */
    public void load() {
        if (!Files.exists(savePath)) {
            LOGGER.atFine().log("No mob_groups.json found: starting fresh");
            return;
        }
        try (Reader reader = Files.newBufferedReader(savePath)) {
            Map<String, MobGroupRecord> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                groups.putAll(loaded);
            }
            LOGGER.atFine().log("Loaded " + groups.size() + " mob group(s) from " + savePath);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load mob_groups.json");
        }
    }

    /** Insert or replace a record and schedule a save. */
    public void put(MobGroupRecord record) {
        groups.put(record.getGroupKey(), record);
        saveAsync();
    }

    /** Look up by group key. */
    public @Nullable MobGroupRecord get(String groupKey) {
        return groups.get(groupKey);
    }

    /** Remove and schedule a save. Returns the removed record or null. */
    public @Nullable MobGroupRecord remove(String groupKey) {
        MobGroupRecord removed = groups.remove(groupKey);
        if (removed != null) saveAsync();
        return removed;
    }

    /** Mark a slot dead and flush. No-op if groupKey unknown or slot not found. */
    public void markSlotDead(String groupKey, int slotIndex) {
        MobGroupRecord record = groups.get(groupKey);
        if (record == null) return;
        for (SlotRecord slot : record.getSlots()) {
            if (slot.getSlotIndex() == slotIndex) {
                slot.setDead(true);
                slot.setCurrentUuid(null);
                saveAsync();
                return;
            }
        }
    }

    /** Update the ephemeral UUID for a slot and flush. */
    public void updateCurrentUuid(String groupKey, int slotIndex, @Nullable UUID newUuid) {
        MobGroupRecord record = groups.get(groupKey);
        if (record == null) return;
        for (SlotRecord slot : record.getSlots()) {
            if (slot.getSlotIndex() == slotIndex) {
                slot.setCurrentUuid(newUuid != null ? newUuid.toString() : null);
                saveAsync();
                return;
            }
        }
    }

    /** All records, for iteration. */
    public Collection<MobGroupRecord> all() {
        return groups.values();
    }

    /** Records owned by a specific player. */
    public List<MobGroupRecord> forOwner(UUID playerUuid) {
        String target = playerUuid.toString();
        List<MobGroupRecord> out = new ArrayList<>();
        for (MobGroupRecord record : groups.values()) {
            if (target.equals(record.getOwnerPlayerUuid())) {
                out.add(record);
            }
        }
        return out;
    }

    /**
     * Debounced async save. Same pattern as {@link com.chonbosmods.settlement.SettlementRegistry#saveAsync}.
     */
    public void saveAsync() {
        if (savePending.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                savePending.set(false);
                try {
                    Files.createDirectories(savePath.getParent());
                    try (Writer writer = Files.newBufferedWriter(savePath)) {
                        GSON.toJson(groups, MAP_TYPE, writer);
                    }
                    LOGGER.atFinest().log("Saved %d mob group(s)", groups.size());
                } catch (IOException e) {
                    LOGGER.atSevere().withCause(e).log("Failed to save mob_groups.json");
                }
            });
        }
    }
}
