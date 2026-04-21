package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;

import java.util.HashMap;
import java.util.Map;

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

    private final Map<String, QuestInstance> primary = new HashMap<>();

    public void add(QuestInstance quest) {
        String id = quest.getQuestId();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("QuestInstance must have a questId before add()");
        }
        primary.put(id, quest);
    }

    public QuestInstance getById(String questId) {
        return primary.get(questId);
    }
}
