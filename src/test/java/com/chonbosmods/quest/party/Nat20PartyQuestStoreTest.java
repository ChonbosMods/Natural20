package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Test
    void queryByPlayerReturnsQuestsWherePlayerIsAccepter() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        QuestInstance q1 = new QuestInstance();
        q1.setQuestId("q1");
        q1.setAccepters(List.of(alice));

        QuestInstance q2 = new QuestInstance();
        q2.setQuestId("q2");
        q2.setAccepters(List.of(alice, bob));

        QuestInstance q3 = new QuestInstance();
        q3.setQuestId("q3");
        q3.setAccepters(List.of(bob));

        store.add(q1);
        store.add(q2);
        store.add(q3);

        assertEquals(Set.of("q1", "q2"), idsOf(store.queryByPlayer(alice)));
        assertEquals(Set.of("q2", "q3"), idsOf(store.queryByPlayer(bob)));
        assertEquals(Set.of(), idsOf(store.queryByPlayer(UUID.randomUUID())));
    }

    @Test
    void queryByPlayerNeverReturnsNull() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        assertNotNull(store.queryByPlayer(UUID.randomUUID()));
    }

    private static Set<String> idsOf(Collection<QuestInstance> quests) {
        return quests.stream().map(QuestInstance::getQuestId).collect(Collectors.toSet());
    }
}
