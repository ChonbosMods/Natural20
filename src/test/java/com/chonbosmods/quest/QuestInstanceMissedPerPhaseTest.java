package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestInstanceMissedPerPhaseTest {

    @Test
    void freshQuest_anyPhase_returnsEmptySet() {
        QuestInstance q = new QuestInstance();
        assertTrue(q.getMissedForPhase(0).isEmpty());
        assertTrue(q.getMissedForPhase(5).isEmpty());
    }

    @Test
    void markMissed_singlePhase_roundtrips() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        q.markMissedForPhase(0, Set.of(alice, bob));

        Set<UUID> got = q.getMissedForPhase(0);
        assertEquals(Set.of(alice, bob), got);
    }

    @Test
    void markMissed_multiplePhases_isolated() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();

        q.markMissedForPhase(0, Set.of(alice));
        q.markMissedForPhase(2, Set.of(bob, carol));

        assertEquals(Set.of(alice), q.getMissedForPhase(0));
        assertTrue(q.getMissedForPhase(1).isEmpty(), "skipped phase should be empty");
        assertEquals(Set.of(bob, carol), q.getMissedForPhase(2));
    }

    @Test
    void markMissed_additive_onSamePhase() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        q.markMissedForPhase(0, Set.of(alice));
        q.markMissedForPhase(0, Set.of(bob));

        assertEquals(Set.of(alice, bob), q.getMissedForPhase(0));
    }

    @Test
    void markMissed_emptySet_noop() {
        QuestInstance q = new QuestInstance();
        q.markMissedForPhase(0, Set.of());
        assertTrue(q.getMissedForPhase(0).isEmpty());
    }

    @Test
    void markMissed_nullSet_noop() {
        QuestInstance q = new QuestInstance();
        q.markMissedForPhase(0, null);
        assertTrue(q.getMissedForPhase(0).isEmpty());
    }

    @Test
    void getMissed_negativeIndex_returnsEmpty() {
        QuestInstance q = new QuestInstance();
        q.markMissedForPhase(0, Set.of(UUID.randomUUID()));
        assertTrue(q.getMissedForPhase(-1).isEmpty());
    }

    @Test
    void accepters_notMutated_byMarkMissed() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        q.setAccepters(List.of(alice, bob));

        q.markMissedForPhase(0, Set.of(bob));

        // Option B invariant: accepters list never mutates on phase-miss.
        assertEquals(List.of(alice, bob), q.getAccepters());
        assertFalse(q.droppedAccepters().contains(bob));
    }
}
