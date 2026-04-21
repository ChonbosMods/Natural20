package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-global, quest-keyed authoritative store for active {@link QuestInstance}s.
 * Every active quest lives in exactly one place here, and every read/write goes
 * through this store.
 *
 * <p>Queries by player go through a secondary index keyed on each accepter's
 * UUID. Completion moves records out of the store and onto per-player
 * {@code Nat20PlayerData.completedQuests} via {@link #turnIn}.
 *
 * <p>Design reference: {@code docs/plans/2026-04-21-party-multiplayer-quest-design.md}
 * §11 Storage architecture.
 */
public class Nat20PartyQuestStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PRIMARY_TYPE =
        new TypeToken<Map<String, QuestInstance>>() {}.getType();

    private final Map<String, QuestInstance> primary = new HashMap<>();
    private final Map<UUID, Set<String>> byPlayer = new HashMap<>();

    public void add(QuestInstance quest) {
        String id = quest.getQuestId();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("QuestInstance must have a questId before add()");
        }
        primary.put(id, quest);
        for (UUID player : quest.getAccepters()) {
            byPlayer.computeIfAbsent(player, k -> new HashSet<>()).add(id);
        }
    }

    public QuestInstance getById(String questId) {
        return primary.get(questId);
    }

    public List<QuestInstance> queryByPlayer(UUID player) {
        Set<String> ids = byPlayer.get(player);
        if (ids == null || ids.isEmpty()) return List.of();
        List<QuestInstance> out = new ArrayList<>(ids.size());
        for (String id : ids) out.add(primary.get(id));
        return out;
    }

    public void remove(String questId) {
        QuestInstance q = primary.remove(questId);
        if (q == null) return;
        for (UUID player : q.getAccepters()) {
            Set<String> ids = byPlayer.get(player);
            if (ids == null) continue;
            ids.remove(questId);
            if (ids.isEmpty()) byPlayer.remove(player);
        }
    }

    /**
     * Callback for turn-in, invoked once per accepter. Implementers record the
     * {@code CompletedQuestRecord} into the per-player data store. The sink
     * receives the live quest instance so the template (reward xp, reward
     * items, etc.) can be read out before the instance is removed.
     */
    @FunctionalInterface
    public interface CompletionSink {
        void record(UUID player, QuestInstance quest);
    }

    /**
     * One-shot migration from the legacy per-player {@code active_quests} JSON
     * stored on {@code Nat20PlayerData} into this quest-keyed store. Each legacy
     * entry becomes a store entry with {@code accepters = [player]}.
     *
     * <p>Idempotent: if the store already contains an entry for a given
     * questId, the legacy copy is dropped (first writer wins). Safe to call
     * repeatedly and on empty/null input.
     */
    public void migratePlayer(UUID player, Map<String, QuestInstance> legacyActive) {
        if (legacyActive == null || legacyActive.isEmpty()) return;
        for (QuestInstance legacy : legacyActive.values()) {
            String id = legacy.getQuestId();
            if (id == null || id.isEmpty()) continue;
            if (primary.containsKey(id)) continue;
            legacy.setAccepters(List.of(player));
            primary.put(id, legacy);
            byPlayer.computeIfAbsent(player, k -> new HashSet<>()).add(id);
        }
    }

    public void turnIn(String questId, CompletionSink sink) {
        QuestInstance q = primary.get(questId);
        if (q == null) return;
        for (UUID player : q.getAccepters()) sink.record(player, q);
        remove(questId);
    }

    public void saveTo(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(file, GSON.toJson(primary, PRIMARY_TYPE));
    }

    public void loadFrom(Path file) throws IOException {
        primary.clear();
        byPlayer.clear();
        if (!Files.exists(file)) return;
        String json = Files.readString(file);
        if (json.isEmpty()) return;
        Map<String, QuestInstance> loaded = GSON.fromJson(json, PRIMARY_TYPE);
        if (loaded == null) return;
        primary.putAll(loaded);
        for (QuestInstance q : loaded.values()) {
            for (UUID player : q.getAccepters()) {
                byPlayer.computeIfAbsent(player, k -> new HashSet<>()).add(q.getQuestId());
            }
        }
    }
}
