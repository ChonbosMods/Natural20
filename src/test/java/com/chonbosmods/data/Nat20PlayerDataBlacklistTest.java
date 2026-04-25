package com.chonbosmods.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Nat20PlayerDataBlacklistTest {

    @Test
    void newPlayerData_hasEmptyBlacklist() {
        Nat20PlayerData data = new Nat20PlayerData();
        assertTrue(data.getBlacklistedQuestIds().isEmpty());
        assertFalse(data.isQuestBlacklisted("any_quest"));
    }

    @Test
    void addBlacklistedQuest_marksQuestAsBlacklisted() {
        Nat20PlayerData data = new Nat20PlayerData();
        data.addBlacklistedQuest("quest_alpha");
        assertTrue(data.isQuestBlacklisted("quest_alpha"));
        assertFalse(data.isQuestBlacklisted("quest_beta"));
        assertEquals(1, data.getBlacklistedQuestIds().size());
    }

    @Test
    void addBlacklistedQuest_isIdempotent() {
        Nat20PlayerData data = new Nat20PlayerData();
        data.addBlacklistedQuest("quest_alpha");
        data.addBlacklistedQuest("quest_alpha");
        assertEquals(1, data.getBlacklistedQuestIds().size());
    }

    @Test
    void getBlacklistedQuestIds_isUnmodifiable() {
        Nat20PlayerData data = new Nat20PlayerData();
        data.addBlacklistedQuest("quest_alpha");
        assertThrows(UnsupportedOperationException.class,
                () -> data.getBlacklistedQuestIds().add("quest_beta"));
    }
}
