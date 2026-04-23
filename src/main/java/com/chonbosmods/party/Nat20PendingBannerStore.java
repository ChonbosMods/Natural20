package com.chonbosmods.party;

import com.chonbosmods.quest.PendingQuestMissedBanner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-global, file-backed side-car for {@link PendingQuestMissedBanner}
 * entries queued for offline players. The in-component field on
 * {@code Nat20PlayerData} cannot be written while the player is offline
 * because the {@code PlayerData} component is only loaded into the ECS store
 * while the player entity is online. This store fills that gap: the proximity
 * gate writes here for offline evictees, and the next
 * {@code PlayerReadyEvent} drains it into the banner pipeline.
 *
 * <p>Lifecycle mirrors {@link Nat20PartyQuestStore}: instantiate at plugin
 * setup, bind the world-scoped save directory via
 * {@link #setSaveDirectory(Path)}, then {@link #load()}. Every public mutator
 * saves synchronously so a crash between mutate and save cannot leave banners
 * only in-memory.
 *
 * <p>Design reference: {@code docs/plans/2026-04-22-party-quest-proximity-and-mlvl-scaling-impl.md}
 * Task 10.
 */
public class Nat20PendingBannerStore {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20PendingBannerStore");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // String-keyed on disk (UUID.toString()) to avoid Gson's complex-map-key
    // ceremony; matches the same convention used by Nat20PartyRegistry.
    private static final Type DISK_TYPE =
        new TypeToken<Map<String, List<PendingQuestMissedBanner>>>() {}.getType();

    private static final String SAVE_FILE_NAME = "pending_banners.json";

    private final Map<UUID, List<PendingQuestMissedBanner>> byPlayer = new HashMap<>();
    private Path saveDirectory;

    /** Bind the world-scoped save directory. Subsequent {@link #save()} /
     *  {@link #load()} calls resolve {@code pending_banners.json} inside it. */
    public void setSaveDirectory(Path dir) {
        this.saveDirectory = dir;
    }

    public void save() throws IOException {
        if (saveDirectory == null) {
            throw new IllegalStateException("saveDirectory not set; call setSaveDirectory first");
        }
        saveTo(saveDirectory.resolve(SAVE_FILE_NAME));
    }

    public void load() throws IOException {
        if (saveDirectory == null) {
            throw new IllegalStateException("saveDirectory not set; call setSaveDirectory first");
        }
        loadFrom(saveDirectory.resolve(SAVE_FILE_NAME));
    }

    /**
     * Append {@code banner} to the queue for {@code playerUuid} and persist.
     * Concurrent writes for the same player are serialized via the containing
     * list under synchronized access on the outer map: call sites run on the
     * world thread already, so contention is bounded.
     */
    public void queue(UUID playerUuid, PendingQuestMissedBanner banner) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid must not be null");
        }
        if (banner == null) {
            throw new IllegalArgumentException("banner must not be null");
        }
        synchronized (byPlayer) {
            byPlayer.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(banner);
        }
        persistOrLog("queue", playerUuid);
    }

    /**
     * Remove and return every pending banner for {@code playerUuid}. Returns
     * an empty list (never null) if no entries exist. Always persists so the
     * drained state is durable: a crash after drain but before the banner
     * fires would otherwise replay on next login, which is acceptable, but
     * persisting the clear is cheaper long-term than leaving stale entries.
     */
    public List<PendingQuestMissedBanner> drain(UUID playerUuid) {
        List<PendingQuestMissedBanner> drained;
        synchronized (byPlayer) {
            List<PendingQuestMissedBanner> existing = byPlayer.remove(playerUuid);
            drained = (existing == null) ? List.of() : new ArrayList<>(existing);
        }
        if (!drained.isEmpty()) {
            persistOrLog("drain", playerUuid);
        }
        return drained;
    }

    /**
     * Remove every pending entry for {@code playerUuid} whose questId matches.
     * Used by the T15 ghost-case purge when an offline banner is superseded by
     * a later turn-in (no banner needed).
     */
    public void removeForQuest(UUID playerUuid, String questId) {
        if (playerUuid == null || questId == null) return;
        boolean changed;
        synchronized (byPlayer) {
            List<PendingQuestMissedBanner> list = byPlayer.get(playerUuid);
            if (list == null) return;
            changed = list.removeIf(b -> questId.equals(b.questId()));
            if (list.isEmpty()) byPlayer.remove(playerUuid);
        }
        if (changed) {
            persistOrLog("removeForQuest", playerUuid);
        }
    }

    /**
     * Walk every player's queue and remove any entries referencing
     * {@code questId}. Invoked by T15 on turn-in so stale pending banners for
     * a completed quest are purged across all offline accepters. Saves once.
     */
    public void removeForQuestAllPlayers(String questId) {
        if (questId == null) return;
        boolean changed = false;
        synchronized (byPlayer) {
            Iterator<Map.Entry<UUID, List<PendingQuestMissedBanner>>> it =
                byPlayer.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, List<PendingQuestMissedBanner>> e = it.next();
                List<PendingQuestMissedBanner> list = e.getValue();
                if (list == null) continue;
                if (list.removeIf(b -> questId.equals(b.questId()))) {
                    changed = true;
                }
                if (list.isEmpty()) it.remove();
            }
        }
        if (changed) {
            try {
                save();
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log(
                    "Failed to persist removeForQuestAllPlayers questId=%s", questId);
                throw new RuntimeException(
                    "Failed to persist removeForQuestAllPlayers for quest " + questId, e);
            }
        }
    }

    public void saveTo(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Map<String, List<PendingQuestMissedBanner>> serializable;
        synchronized (byPlayer) {
            serializable = new HashMap<>(byPlayer.size());
            for (Map.Entry<UUID, List<PendingQuestMissedBanner>> e : byPlayer.entrySet()) {
                serializable.put(e.getKey().toString(), new ArrayList<>(e.getValue()));
            }
        }
        Files.writeString(file, GSON.toJson(serializable, DISK_TYPE));
    }

    public void loadFrom(Path file) throws IOException {
        synchronized (byPlayer) {
            byPlayer.clear();
        }
        if (!Files.exists(file)) return;
        String json = Files.readString(file);
        if (json.isEmpty()) return;
        Map<String, List<PendingQuestMissedBanner>> loaded = GSON.fromJson(json, DISK_TYPE);
        if (loaded == null) return;
        synchronized (byPlayer) {
            for (Map.Entry<String, List<PendingQuestMissedBanner>> e : loaded.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) continue;
                UUID uuid;
                try {
                    uuid = UUID.fromString(e.getKey());
                } catch (IllegalArgumentException ex) {
                    LOGGER.atWarning().log(
                        "Skipping malformed UUID key in pending_banners.json: %s", e.getKey());
                    continue;
                }
                byPlayer.put(uuid, new ArrayList<>(e.getValue()));
            }
        }
    }

    private void persistOrLog(String op, UUID playerUuid) {
        try {
            save();
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log(
                "Failed to persist %s for player=%s", op, playerUuid);
            throw new RuntimeException(
                "Failed to persist " + op + " for player " + playerUuid, e);
        }
    }
}
