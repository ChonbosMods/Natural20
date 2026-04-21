package com.chonbosmods.quest;

import com.chonbosmods.data.Nat20PlayerData;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the static helper that reads the legacy {@code active_quests} JSON out
 * of {@link Nat20PlayerData}'s questFlags map. Migration on PlayerReady will
 * call this to move stored quests into {@code Nat20PartyQuestStore}.
 */
class QuestStateManagerLegacyReaderTest {

    @Test
    void readerReturnsEmptyMapWhenLegacyKeyMissing() {
        Nat20PlayerData data = new Nat20PlayerData();
        assertTrue(QuestStateManager.readLegacyActiveQuests(data).isEmpty());
    }

    @Test
    void readerReturnsEmptyMapWhenLegacyValueIsEmpty() {
        Nat20PlayerData data = new Nat20PlayerData();
        data.setQuestData("active_quests", "");
        assertTrue(QuestStateManager.readLegacyActiveQuests(data).isEmpty());
    }

    @Test
    void readerDeserializesLegacyJsonIntoQuestInstances() {
        Nat20PlayerData data = new Nat20PlayerData();
        data.setQuestData("active_quests",
            "{\"quest-alpha\":{\"questId\":\"quest-alpha\",\"conflictCount\":1,\"maxConflicts\":3}}");

        Map<String, QuestInstance> loaded = QuestStateManager.readLegacyActiveQuests(data);

        assertEquals(Set.of("quest-alpha"), loaded.keySet());
        QuestInstance q = loaded.get("quest-alpha");
        assertEquals("quest-alpha", q.getQuestId());
        assertEquals(1, q.getConflictCount());
        assertEquals(3, q.getMaxConflicts());
    }

    @Test
    void readerReturnsEmptyMapWhenJsonIsMalformed() {
        Nat20PlayerData data = new Nat20PlayerData();
        data.setQuestData("active_quests", "{ not valid json");
        assertTrue(QuestStateManager.readLegacyActiveQuests(data).isEmpty());
    }

    @Test
    void clearLegacyActiveQuestsWipesTheKey() {
        Nat20PlayerData data = new Nat20PlayerData();
        data.setQuestData("active_quests", "{\"q\":{\"questId\":\"q\"}}");
        QuestStateManager.clearLegacyActiveQuests(data);
        assertTrue(QuestStateManager.readLegacyActiveQuests(data).isEmpty());
    }
}
