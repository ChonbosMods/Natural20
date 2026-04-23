package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestInstanceDroppedAcceptersTest {

    @Test
    void eligibleAccepters_excludesDropped() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        q.setAccepters(List.of(alice, bob, carol));

        q.dropAccepter(bob);

        Set<UUID> eligible = q.eligibleAccepters();
        assertEquals(Set.of(alice, carol), eligible);
    }

    @Test
    void isEligible_false_whenDropped() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        q.setAccepters(List.of(alice));

        assertTrue(q.isEligible(alice));
        q.dropAccepter(alice);
        assertFalse(q.isEligible(alice));
    }

    @Test
    void isEligible_false_whenNotAccepter() {
        QuestInstance q = new QuestInstance();
        q.setAccepters(List.of(UUID.randomUUID()));
        assertFalse(q.isEligible(UUID.randomUUID()));
    }

    @Test
    void dropAccepter_isIdempotent() {
        QuestInstance q = new QuestInstance();
        UUID alice = UUID.randomUUID();
        q.setAccepters(List.of(alice));
        q.dropAccepter(alice);
        q.dropAccepter(alice);
        assertEquals(1, q.droppedAccepters().size());
    }

    @Test
    void droppedAccepters_freshQuest_isEmpty() {
        QuestInstance q = new QuestInstance();
        assertTrue(q.droppedAccepters().isEmpty());
    }
}
