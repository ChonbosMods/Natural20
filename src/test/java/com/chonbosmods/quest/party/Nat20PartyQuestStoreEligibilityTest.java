package com.chonbosmods.quest.party;

import com.chonbosmods.quest.QuestInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyQuestStoreEligibilityTest {

    @TempDir Path tmp;
    Nat20PartyQuestStore store;
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();

    @BeforeEach
    void setup() {
        store = new Nat20PartyQuestStore();
        store.setSaveDirectory(tmp);
    }

    @Test
    void dropAccepter_removesFromPerPlayerIndex() throws Exception {
        QuestInstance q = new QuestInstance();
        q.setQuestId("q1");
        q.setAccepters(List.of(alice, bob));
        store.add(q);

        assertEquals(1, store.queryByPlayer(alice).size());
        assertEquals(1, store.queryByPlayer(bob).size());

        store.dropAccepter("q1", bob);

        assertEquals(1, store.queryByPlayer(alice).size());
        assertEquals(0, store.queryByPlayer(bob).size(), "bob should no longer see the quest");
    }

    @Test
    void dropAccepter_missingQuest_isNoop() throws Exception {
        store.dropAccepter("nonexistent", alice);
        // must not throw
    }

    @Test
    void dropAccepter_persistsAcrossReload() throws Exception {
        QuestInstance q = new QuestInstance();
        q.setQuestId("q1");
        q.setAccepters(List.of(alice, bob));
        store.add(q);
        store.dropAccepter("q1", bob);

        Nat20PartyQuestStore reloaded = new Nat20PartyQuestStore();
        reloaded.setSaveDirectory(tmp);
        reloaded.load();

        assertEquals(0, reloaded.queryByPlayer(bob).size());
        assertTrue(reloaded.get("q1").droppedAccepters().contains(bob));
    }
}
