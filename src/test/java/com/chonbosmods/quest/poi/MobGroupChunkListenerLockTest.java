package com.chonbosmods.quest.poi;

import com.chonbosmods.progression.Nat20MobGroupSpawner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MobGroupChunkListenerLockTest {

    @Test
    void withGroupLockRunsOnceWhenUncontended() {
        MobGroupChunkListener listener = new MobGroupChunkListener(
                /* registry */ null, /* spawner */ (Nat20MobGroupSpawner) null);
        AtomicInteger count = new AtomicInteger();
        listener.withGroupLock("g1", count::incrementAndGet);
        assertEquals(1, count.get());
    }

    @Test
    void withGroupLockSkipsWhenReentrant() {
        MobGroupChunkListener listener = new MobGroupChunkListener(null, (Nat20MobGroupSpawner) null);
        AtomicInteger outer = new AtomicInteger();
        AtomicInteger inner = new AtomicInteger();
        listener.withGroupLock("g1", () -> {
            outer.incrementAndGet();
            listener.withGroupLock("g1", inner::incrementAndGet);
        });
        assertEquals(1, outer.get());
        assertEquals(0, inner.get(), "reentrant call on same key must be skipped");
    }

    @Test
    void withGroupLockReleasesOnException() {
        MobGroupChunkListener listener = new MobGroupChunkListener(null, (Nat20MobGroupSpawner) null);
        assertThrows(RuntimeException.class, () -> listener.withGroupLock("g1", () -> {
            throw new RuntimeException("boom");
        }));
        AtomicInteger count = new AtomicInteger();
        listener.withGroupLock("g1", count::incrementAndGet);
        assertEquals(1, count.get(), "lock must be released even after exception");
    }
}
