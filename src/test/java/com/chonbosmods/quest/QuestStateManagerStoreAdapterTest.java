package com.chonbosmods.quest;

import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link QuestStateManager}, when constructed with a
 * {@link Nat20PartyQuestStore}, routes reads and writes through the store
 * rather than the legacy per-player questFlags JSON.
 */
class QuestStateManagerStoreAdapterTest {

    @Test
    void getActiveQuestsReturnsStoreContentsFilteredByPlayer() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        Nat20PlayerData aliceData = new Nat20PlayerData();
        UUID alice = UUID.randomUUID();
        aliceData.setPlayerUuid(alice);

        Nat20PlayerData bobData = new Nat20PlayerData();
        UUID bob = UUID.randomUUID();
        bobData.setPlayerUuid(bob);

        QuestInstance q = new QuestInstance();
        q.setQuestId("shared");
        q.setAccepters(List.of(alice, bob));
        store.add(q);

        assertEquals(Set.of("shared"), mgr.getActiveQuests(aliceData).keySet());
        assertEquals(Set.of("shared"), mgr.getActiveQuests(bobData).keySet());
    }

    @Test
    void addQuestStoresWithCurrentPlayerAsSoloAccepter() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        Nat20PlayerData aliceData = new Nat20PlayerData();
        UUID alice = UUID.randomUUID();
        aliceData.setPlayerUuid(alice);

        QuestInstance q = new QuestInstance();
        q.setQuestId("solo-accept");

        mgr.addQuest(aliceData, q);

        assertNotNull(store.getById("solo-accept"));
        assertEquals(List.of(alice), store.getById("solo-accept").getAccepters(),
            "legacy addQuest path defaults accepters to [self] when empty");
    }

    @Test
    void addQuestPreservesExplicitAcceptersWhenAlreadySet() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        Nat20PlayerData aliceData = new Nat20PlayerData();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        aliceData.setPlayerUuid(alice);

        QuestInstance q = new QuestInstance();
        q.setQuestId("party-accept");
        q.setAccepters(List.of(alice, bob)); // already snapshotted by acceptForParty

        mgr.addQuest(aliceData, q);

        assertEquals(List.of(alice, bob), store.getById("party-accept").getAccepters());
    }

    @Test
    void getQuestReturnsNullIfPlayerIsNotAnAccepter() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        Nat20PlayerData outsiderData = new Nat20PlayerData();
        outsiderData.setPlayerUuid(UUID.randomUUID());

        QuestInstance q = new QuestInstance();
        q.setQuestId("someone-elses");
        q.setAccepters(List.of(UUID.randomUUID()));
        store.add(q);

        assertNull(mgr.getQuest(outsiderData, "someone-elses"),
            "visibility is gated by accepters membership");
    }

    @Test
    void removeQuestRemovesFromStore() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        Nat20PlayerData aliceData = new Nat20PlayerData();
        UUID alice = UUID.randomUUID();
        aliceData.setPlayerUuid(alice);

        QuestInstance q = new QuestInstance();
        q.setQuestId("doomed");
        q.setAccepters(List.of(alice));
        store.add(q);

        mgr.removeQuest(aliceData, "doomed");

        assertNull(store.getById("doomed"));
    }

    @Test
    void mutationsToReturnedQuestAreLiveInStore() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        Nat20PlayerData aliceData = new Nat20PlayerData();
        UUID alice = UUID.randomUUID();
        aliceData.setPlayerUuid(alice);

        QuestInstance q = new QuestInstance();
        q.setQuestId("live");
        q.setAccepters(List.of(alice));
        q.setMaxConflicts(3);
        store.add(q);

        QuestInstance fetched = mgr.getQuest(aliceData, "live");
        fetched.incrementConflictCount();

        assertEquals(1, mgr.getQuest(aliceData, "live").getConflictCount(),
            "adapter must return the store's live reference");
    }
}
