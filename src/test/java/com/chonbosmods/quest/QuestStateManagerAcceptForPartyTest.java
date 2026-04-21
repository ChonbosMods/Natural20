package com.chonbosmods.quest;

import com.chonbosmods.party.Nat20Party;
import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestStateManagerAcceptForPartyTest {

    @Test
    void acceptForPartySnapshotsCurrentMembersAsAccepters() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Nat20Party party = Nat20Party.ofSolo(alice);
        party.addMember(bob);

        QuestInstance q = new QuestInstance();
        q.setQuestId("party-quest");

        mgr.acceptForParty(party, q);

        QuestInstance stored = store.getById("party-quest");
        assertNotNull(stored);
        assertEquals(List.of(alice, bob), stored.getAccepters());
    }

    @Test
    void acceptForPartyFreezesAcceptersAgainstLaterPartyChurn() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Nat20Party party = Nat20Party.ofSolo(alice);
        party.addMember(bob);

        QuestInstance q = new QuestInstance();
        q.setQuestId("frozen");
        mgr.acceptForParty(party, q);

        party.removeMember(bob); // bob leaves after accept

        assertEquals(List.of(alice, bob), store.getById("frozen").getAccepters(),
            "accepters is frozen at accept time and does not track party membership");
    }

    @Test
    void acceptForPartyWorksForSoloPartyOfOne() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        QuestStateManager mgr = new QuestStateManager(store);

        UUID alice = UUID.randomUUID();
        Nat20Party solo = Nat20Party.ofSolo(alice);

        QuestInstance q = new QuestInstance();
        q.setQuestId("solo");
        mgr.acceptForParty(solo, q);

        assertEquals(List.of(alice), store.getById("solo").getAccepters());
    }

    @Test
    void acceptForPartyRequiresStoreBackedManager() {
        QuestStateManager legacyMgr = new QuestStateManager();
        UUID alice = UUID.randomUUID();
        Nat20Party solo = Nat20Party.ofSolo(alice);
        QuestInstance q = new QuestInstance();
        q.setQuestId("legacy-mode");

        assertThrows(IllegalStateException.class, () -> legacyMgr.acceptForParty(solo, q),
            "acceptForParty is a store-only API; using it on a legacy-mode manager must fail loudly");
    }
}
