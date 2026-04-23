package com.chonbosmods.party;

import com.chonbosmods.quest.PendingQuestMissedBanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Nat20PendingBannerStoreTest {

    @TempDir Path tmp;
    Nat20PendingBannerStore store;
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();

    @BeforeEach
    void setup() {
        store = new Nat20PendingBannerStore();
        store.setSaveDirectory(tmp);
    }

    @Test
    void queue_then_drain_roundTrips() {
        PendingQuestMissedBanner b1 = new PendingQuestMissedBanner("q1", "Find Jiub", 100L);
        PendingQuestMissedBanner b2 = new PendingQuestMissedBanner("q2", "Slay the orcs", 200L);
        store.queue(alice, b1);
        store.queue(alice, b2);

        List<PendingQuestMissedBanner> drained = store.drain(alice);

        assertEquals(2, drained.size());
        assertEquals("q1", drained.get(0).questId());
        assertEquals("q2", drained.get(1).questId());
        // Subsequent drain is empty — state was cleared.
        assertTrue(store.drain(alice).isEmpty());
    }

    @Test
    void drain_forUnknownPlayer_returnsEmptyList() {
        assertTrue(store.drain(UUID.randomUUID()).isEmpty());
    }

    @Test
    void queue_persistsAcrossReload() throws Exception {
        PendingQuestMissedBanner b = new PendingQuestMissedBanner("q1", "Hello", 42L);
        store.queue(alice, b);

        Nat20PendingBannerStore reloaded = new Nat20PendingBannerStore();
        reloaded.setSaveDirectory(tmp);
        reloaded.load();

        List<PendingQuestMissedBanner> drained = reloaded.drain(alice);
        assertEquals(1, drained.size());
        assertEquals("q1", drained.get(0).questId());
        assertEquals("Hello", drained.get(0).topicHeader());
        assertEquals(42L, drained.get(0).queuedAtEpochMs());
    }

    @Test
    void removeForQuest_onlyRemovesMatchingEntries() {
        store.queue(alice, new PendingQuestMissedBanner("q1", "A", 1L));
        store.queue(alice, new PendingQuestMissedBanner("q2", "B", 2L));
        store.queue(alice, new PendingQuestMissedBanner("q1", "A-dup", 3L));

        store.removeForQuest(alice, "q1");

        List<PendingQuestMissedBanner> remaining = store.drain(alice);
        assertEquals(1, remaining.size());
        assertEquals("q2", remaining.get(0).questId());
    }

    @Test
    void removeForQuestAllPlayers_sweepsEveryone() throws Exception {
        store.queue(alice, new PendingQuestMissedBanner("q1", "A", 1L));
        store.queue(bob, new PendingQuestMissedBanner("q1", "A-bob", 1L));
        store.queue(bob, new PendingQuestMissedBanner("q2", "B", 2L));

        store.removeForQuestAllPlayers("q1");

        assertTrue(store.drain(alice).isEmpty(), "alice's q1 banner should be gone");
        List<PendingQuestMissedBanner> bobRemaining = store.drain(bob);
        assertEquals(1, bobRemaining.size());
        assertEquals("q2", bobRemaining.get(0).questId());

        // Also verify persistence: reload and confirm the removal stuck.
        Nat20PendingBannerStore reloaded = new Nat20PendingBannerStore();
        reloaded.setSaveDirectory(tmp);
        reloaded.load();
        assertTrue(reloaded.drain(alice).isEmpty());
        // bob was fully drained above (and saved) — nothing left.
        assertTrue(reloaded.drain(bob).isEmpty());
    }

    @Test
    void load_onMissingFile_isNoop() throws Exception {
        // Fresh tempdir, no save file present.
        Nat20PendingBannerStore fresh = new Nat20PendingBannerStore();
        fresh.setSaveDirectory(tmp);
        fresh.load();
        assertTrue(fresh.drain(alice).isEmpty());
    }
}
