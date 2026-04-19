package com.chonbosmods.quest;

import com.chonbosmods.data.Nat20PlayerData;
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

    public Map<String, QuestInstance> getActiveQuests(Nat20PlayerData data) {
        String json = data.getQuestData(KEY_ACTIVE_QUESTS);
        if (json == null || json.isEmpty()) return new HashMap<>();
        Map<String, QuestInstance> result = GSON.fromJson(json, QUEST_MAP_TYPE);
        return result != null ? result : new HashMap<>();
    }

    public void saveActiveQuests(Nat20PlayerData data, Map<String, QuestInstance> quests) {
        data.setQuestData(KEY_ACTIVE_QUESTS, GSON.toJson(quests, QUEST_MAP_TYPE));
    }

    public void addQuest(Nat20PlayerData data, QuestInstance quest) {
        Map<String, QuestInstance> quests = getActiveQuests(data);
        quests.put(quest.getQuestId(), quest);
        saveActiveQuests(data, quests);
    }

    public void removeQuest(Nat20PlayerData data, String questId) {
        Map<String, QuestInstance> quests = getActiveQuests(data);
        quests.remove(questId);
        saveActiveQuests(data, quests);
    }

    public QuestInstance getQuest(Nat20PlayerData data, String questId) {
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
        // Task 4 will populate questName + finalObjectiveText from the live quest snapshot.
        // For now, append an id-only record so call sites and the codec round-trip work.
        for (CompletedQuestRecord existing : data.getCompletedQuests()) {
            if (questId.equals(existing.getQuestId())) {
                removeQuest(data, questId);
                return;
            }
        }
        data.getCompletedQuests().add(new CompletedQuestRecord(questId, "", ""));
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
