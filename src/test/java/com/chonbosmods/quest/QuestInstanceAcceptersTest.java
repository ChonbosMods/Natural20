package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestInstanceAcceptersTest {

    @Test
    void newInstanceHasEmptyAcceptersByDefault() {
        QuestInstance q = new QuestInstance();
        assertNotNull(q.getAccepters(), "accepters must be non-null for legacy-deserialized instances");
        assertTrue(q.getAccepters().isEmpty());
    }

    @Test
    void setAcceptersStoresProvidedUuids() {
        QuestInstance q = new QuestInstance();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        q.setAccepters(List.of(a, b));
        assertEquals(List.of(a, b), q.getAccepters());
    }

    @Test
    void hasAccepterReturnsTrueForMemberAndFalseForNon() {
        QuestInstance q = new QuestInstance();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        q.setAccepters(List.of(a));
        assertTrue(q.hasAccepter(a));
        assertFalse(q.hasAccepter(b));
    }

    @Test
    void setAcceptersWithNullDefaultsToEmptyList() {
        QuestInstance q = new QuestInstance();
        q.setAccepters(null);
        assertNotNull(q.getAccepters());
        assertTrue(q.getAccepters().isEmpty());
    }
}
