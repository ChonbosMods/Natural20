package com.chonbosmods.quest;

import com.chonbosmods.quest.party.Nat20PartyQuestStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class Nat20QuestProximityEnforcerTest {

    @TempDir Path tmp;
    Nat20PartyQuestStore store;
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    List<UUID> onlineBannersFired = new ArrayList<>();
    List<UUID> offlineBannersQueued = new ArrayList<>();

    @BeforeEach
    void setup() {
        store = new Nat20PartyQuestStore();
        store.setSaveDirectory(tmp);
    }

    @Test
    void sweep_evictsBob_andFiresOnlineBanner() {
        QuestInstance q = new QuestInstance();
        q.setQuestId("q1");
        q.setAccepters(List.of(alice, bob));
        q.getVariableBindings().put("quest_topic_header", "Saving the Orchard");
        store.add(q);

        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) :
            u.equals(bob)   ? Optional.of(new double[]{500,0,0}) :
            Optional.empty();
        Predicate<UUID> online = u -> true;  // both online

        List<PendingQuestMissedBanner> onlinePending = new ArrayList<>();
        Nat20QuestProximityEnforcer.sweepForPhaseCompletion(
            q, alice, new double[]{0,0,0},
            positions, online,
            store,
            (uuid, pending) -> { onlineBannersFired.add(uuid); onlinePending.add(pending); },
            (uuid, pending) -> offlineBannersQueued.add(uuid));

        assertTrue(q.droppedAccepters().contains(bob));
        assertEquals(List.of(bob), onlineBannersFired);
        assertTrue(offlineBannersQueued.isEmpty());
        assertEquals(1, onlinePending.size());
        assertEquals("Saving the Orchard", onlinePending.get(0).topicHeader());
        assertEquals("q1", onlinePending.get(0).questId());
    }

    @Test
    void sweep_offlineBob_queuesBannerInsteadOfFiring() {
        QuestInstance q = new QuestInstance();
        q.setQuestId("q1");
        q.setAccepters(List.of(alice, bob));
        store.add(q);

        Function<UUID, Optional<double[]>> positions = u ->
            u.equals(alice) ? Optional.of(new double[]{0,0,0}) : Optional.empty();
        Predicate<UUID> online = u -> u.equals(alice);

        Nat20QuestProximityEnforcer.sweepForPhaseCompletion(
            q, alice, new double[]{0,0,0},
            positions, online,
            store,
            (uuid, pending) -> onlineBannersFired.add(uuid),
            (uuid, pending) -> offlineBannersQueued.add(uuid));

        assertEquals(List.of(bob), offlineBannersQueued);
        assertTrue(onlineBannersFired.isEmpty());
    }
}
