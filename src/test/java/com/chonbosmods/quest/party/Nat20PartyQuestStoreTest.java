package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyQuestStoreTest {

    @Test
    void addAndGetByIdRoundTrip() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestInstance q = new QuestInstance();
        q.setQuestId("q-001");
        q.setAccepters(List.of(UUID.randomUUID()));

        store.add(q);

        assertSame(q, store.getById("q-001"));
    }

    @Test
    void getByIdReturnsNullForUnknown() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        assertNull(store.getById("nope"));
    }

    @Test
    void addRejectsQuestWithoutQuestId() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestInstance q = new QuestInstance();
        q.setAccepters(List.of(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> store.add(q));
    }
}
