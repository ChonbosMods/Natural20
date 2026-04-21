package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
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

    @Test
    void removeDeletesFromPrimaryAndIndex() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        UUID alice = UUID.randomUUID();
        QuestInstance q = new QuestInstance();
        q.setQuestId("gone");
        q.setAccepters(List.of(alice));
        store.add(q);

        store.remove("gone");

        assertNull(store.getById("gone"));
        assertTrue(store.queryByPlayer(alice).isEmpty());
    }

    @Test
    void removeOfUnknownIdIsNoOp() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        assertDoesNotThrow(() -> store.remove("never-existed"));
    }

    @Test
    void mutationsOnStoredInstancePersistAcrossReads() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        UUID alice = UUID.randomUUID();
        QuestInstance q = new QuestInstance();
        q.setQuestId("mut");
        q.setAccepters(List.of(alice));
        q.setMaxConflicts(3);
        store.add(q);

        QuestInstance first = store.getById("mut");
        first.incrementConflictCount();

        QuestInstance second = store.getById("mut");
        assertEquals(1, second.getConflictCount(),
            "store must return the same live instance, not a copy (avoids the transient-deserialization trap)");
        assertSame(first, second);
    }

    @Test
    void removeCleansIndexForMultiAccepterQuest() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        QuestInstance q = new QuestInstance();
        q.setQuestId("shared-gone");
        q.setAccepters(List.of(alice, bob));
        store.add(q);

        store.remove("shared-gone");

        assertTrue(store.queryByPlayer(alice).isEmpty());
        assertTrue(store.queryByPlayer(bob).isEmpty());
    }

    @Test
    void saveAndLoadRoundTripsAllQuests(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("party_quests.json");

        Nat20PartyQuestStore out = new Nat20PartyQuestStore();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        QuestInstance q1 = new QuestInstance();
        q1.setQuestId("q1");
        q1.setAccepters(List.of(alice, bob));
        q1.setMaxConflicts(2);
        out.add(q1);

        out.saveTo(file);

        Nat20PartyQuestStore in = new Nat20PartyQuestStore();
        in.loadFrom(file);

        QuestInstance loaded = in.getById("q1");
        assertNotNull(loaded);
        assertEquals(List.of(alice, bob), loaded.getAccepters());
        assertEquals(2, loaded.getMaxConflicts());

        assertEquals(1, in.queryByPlayer(alice).size(),
            "secondary index must be rebuilt on load");
        assertEquals(1, in.queryByPlayer(bob).size());
    }

    @Test
    void loadFromMissingFileStartsEmpty(@TempDir Path tmp) throws Exception {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        store.loadFrom(tmp.resolve("nope.json"));
        assertNull(store.getById("anything"));
    }

    @Test
    void turnInRecordsCompletionForEveryAccepterAndRemovesQuest() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        QuestInstance q = new QuestInstance();
        q.setQuestId("done");
        q.setAccepters(List.of(alice, bob));
        store.add(q);

        List<UUID> recordedFor = new ArrayList<>();
        store.turnIn("done", (player, inst) -> recordedFor.add(player));

        assertEquals(List.of(alice, bob), recordedFor,
            "every accepter must receive a completion record, in accepters order");
        assertNull(store.getById("done"));
        assertTrue(store.queryByPlayer(alice).isEmpty());
        assertTrue(store.queryByPlayer(bob).isEmpty());
    }

    @Test
    void turnInUnknownIdDoesNothing() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        List<UUID> recordedFor = new ArrayList<>();
        store.turnIn("nope", (p, i) -> recordedFor.add(p));
        assertTrue(recordedFor.isEmpty());
    }

    @Test
    void turnInPassesTheLiveQuestInstanceToSink() {
        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        UUID alice = UUID.randomUUID();
        QuestInstance q = new QuestInstance();
        q.setQuestId("same-ref");
        q.setAccepters(List.of(alice));
        store.add(q);

        List<QuestInstance> seen = new ArrayList<>();
        store.turnIn("same-ref", (p, inst) -> seen.add(inst));

        assertEquals(1, seen.size());
        assertSame(q, seen.get(0), "sink receives the authoritative live instance for reward dispense");
    }

    @Test
    void loadClearsPriorStateBeforeLoading(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("pq.json");

        Nat20PartyQuestStore sink = new Nat20PartyQuestStore();
        UUID alice = UUID.randomUUID();
        QuestInstance q = new QuestInstance();
        q.setQuestId("persisted");
        q.setAccepters(List.of(alice));
        sink.add(q);
        sink.saveTo(file);

        Nat20PartyQuestStore store = new Nat20PartyQuestStore();
        UUID bob = UUID.randomUUID();
        QuestInstance pre = new QuestInstance();
        pre.setQuestId("stale");
        pre.setAccepters(List.of(bob));
        store.add(pre);

        store.loadFrom(file);

        assertNull(store.getById("stale"), "load must wipe pre-existing in-memory state");
        assertNotNull(store.getById("persisted"));
        assertTrue(store.queryByPlayer(bob).isEmpty());
    }

    private static Set<String> idsOf(Collection<QuestInstance> quests) {
        return quests.stream().map(QuestInstance::getQuestId).collect(Collectors.toSet());
    }
}
