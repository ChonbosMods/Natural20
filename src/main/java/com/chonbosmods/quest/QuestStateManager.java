package com.chonbosmods.quest;

import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.party.Nat20Party;
import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

public class QuestStateManager {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String KEY_ACTIVE_QUESTS = "active_quests";
    private static final String KEY_ACTIVE_REFS = "active_references";

    private static final Type QUEST_MAP_TYPE = new TypeToken<Map<String, QuestInstance>>() {}.getType();
    private static final Type REF_MAP_TYPE = new TypeToken<Map<String, ReferenceState>>() {}.getType();

    /** When non-null, active-quest state lives in the store and this manager
     *  routes reads/writes through it. When null, the legacy per-player JSON
     *  storage is used (transitional: will be removed once all call sites flip). */
    private final Nat20PartyQuestStore store;

    public QuestStateManager() {
        this.store = null;
    }

    public QuestStateManager(Nat20PartyQuestStore store) {
        this.store = store;
    }

    public Map<String, QuestInstance> getActiveQuests(Nat20PlayerData data) {
        if (store != null) {
            UUID uuid = data.getPlayerUuid();
            if (uuid == null) return new HashMap<>();
            Map<String, QuestInstance> out = new HashMap<>();
            for (QuestInstance q : store.queryByPlayer(uuid)) {
                out.put(q.getQuestId(), q);
            }
            return out;
        }
        String json = data.getQuestData(KEY_ACTIVE_QUESTS);
        if (json == null || json.isEmpty()) return new HashMap<>();
        Map<String, QuestInstance> result = GSON.fromJson(json, QUEST_MAP_TYPE);
        return result != null ? result : new HashMap<>();
    }

    public void saveActiveQuests(Nat20PlayerData data, Map<String, QuestInstance> quests) {
        if (store != null) {
            // No-op under the store model: store is the authoritative home and
            // mutations on returned live references are already visible. File
            // persistence is flushed via Nat20PartyQuestStore.saveTo on the
            // plugin's save cadence, not per-call.
            return;
        }
        data.setQuestData(KEY_ACTIVE_QUESTS, GSON.toJson(quests, QUEST_MAP_TYPE));
    }

    public void addQuest(Nat20PlayerData data, QuestInstance quest) {
        if (store != null) {
            if (quest.getAccepters().isEmpty()) {
                UUID uuid = data.getPlayerUuid();
                if (uuid != null) quest.setAccepters(List.of(uuid));
            }
            store.add(quest);
            return;
        }
        Map<String, QuestInstance> quests = getActiveQuests(data);
        quests.put(quest.getQuestId(), quest);
        saveActiveQuests(data, quests);
    }

    public void removeQuest(Nat20PlayerData data, String questId) {
        if (store != null) {
            store.remove(questId);
            return;
        }
        Map<String, QuestInstance> quests = getActiveQuests(data);
        quests.remove(questId);
        saveActiveQuests(data, quests);
    }

    /**
     * Snapshot the party's current members into the quest as its frozen
     * accepters list, then store the quest. This is the single entry point for
     * accepting a quest on behalf of a party; it enforces the design rule that
     * accepters is captured at accept time and never mutates afterward.
     */
    public void acceptForParty(Nat20Party party, QuestInstance quest) {
        if (store == null) {
            throw new IllegalStateException(
                "acceptForParty requires the store-backed QuestStateManager");
        }
        quest.setAccepters(List.copyOf(party.getMembers()));
        store.add(quest);
    }

    public QuestInstance getQuest(Nat20PlayerData data, String questId) {
        if (store != null) {
            QuestInstance q = store.getById(questId);
            if (q == null) return null;
            UUID uuid = data.getPlayerUuid();
            if (uuid == null || !q.hasAccepter(uuid)) return null;
            return q;
        }
        return getActiveQuests(data).get(questId);
    }

    public Set<String> getCompletedQuestIds(Nat20PlayerData data) {
        Set<String> ids = new HashSet<>();
        for (CompletedQuestRecord record : data.getCompletedQuests()) {
            ids.add(record.getQuestId());
        }
        return ids;
    }

    public void markQuestCompleted(Nat20PlayerData data, String questId) {
        // Snapshot the live quest's display name + final objective text so the
        // Completed tab in the Quest Log keeps rendering the right strings even
        // after the procedural QuestInstance is dropped from the active map.
        //
        // Name source mirrors CharacterSheetPage.resolveQuestName: prefer the
        // authored flavor topic label (quest_topic_header) so the Completed
        // tab's title row doesn't just duplicate the objective text shown
        // underneath it. quest_objective_summary stays the dedicated objective
        // source.
        QuestInstance instance = getQuest(data, questId);
        String questName = questId;
        String finalObjectiveText = "";
        if (instance != null) {
            Map<String, String> bindings = instance.getVariableBindings();
            String sitFallback = instance.getSituationId() != null ? instance.getSituationId() : questId;
            if (bindings != null) {
                questName = bindings.getOrDefault("quest_topic_header",
                    bindings.getOrDefault("subject_name", sitFallback));
                finalObjectiveText = bindings.getOrDefault("quest_objective_summary", "");
            } else {
                questName = sitFallback;
            }
        }

        // Drop any prior record for this questId so re-completion (e.g. a quest
        // that gets re-issued and re-finished) refreshes the snapshot rather
        // than freezing the first one forever (Task 2 review I-1).
        data.getCompletedQuests().removeIf(r -> questId.equals(r.getQuestId()));

        // Prepend so the Completed tab renders most-recent-first (Task 19
        // expects index 0 to be newest; Task 2 review I-2).
        data.getCompletedQuests().add(0, new CompletedQuestRecord(questId, questName, finalObjectiveText));

        removeQuest(data, questId);
    }

    public Map<String, ReferenceState> getActiveReferences(Nat20PlayerData data) {
        String json = data.getQuestData(KEY_ACTIVE_REFS);
        if (json == null || json.isEmpty()) return new HashMap<>();
        Map<String, ReferenceState> result = GSON.fromJson(json, REF_MAP_TYPE);
        return result != null ? result : new HashMap<>();
    }

    public void saveActiveReferences(Nat20PlayerData data, Map<String, ReferenceState> refs) {
        data.setQuestData(KEY_ACTIVE_REFS, GSON.toJson(refs, REF_MAP_TYPE));
    }

    public void addReference(Nat20PlayerData data, ReferenceState ref) {
        Map<String, ReferenceState> refs = getActiveReferences(data);
        refs.put(ref.getReferenceId(), ref);
        saveActiveReferences(data, refs);
    }

    public void removeReference(Nat20PlayerData data, String referenceId) {
        Map<String, ReferenceState> refs = getActiveReferences(data);
        refs.remove(referenceId);
        saveActiveReferences(data, refs);
    }
}
