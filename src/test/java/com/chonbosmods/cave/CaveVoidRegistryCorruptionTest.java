package com.chonbosmods.cave;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the voidsByCell save/load corruption that crashed
 * plugin startup on 2026-04-19: concurrent scanner-thread mutations racing
 * a Gson write produced malformed JSON, and load() propagated the syntax
 * exception instead of recovering.
 */
class CaveVoidRegistryCorruptionTest {

    private static CaveVoidRegistry newRegistry(Path save) {
        CaveVoidRegistry reg = new CaveVoidRegistry();
        reg.setSaveFile(save);
        return reg;
    }


    @Test
    void loadRecoversFromMalformedJson(@TempDir Path tmp) throws IOException {
        Path save = tmp.resolve("cave_voids.json");
        // Shape that actually killed the server: a nested array where an int was expected.
        Files.writeString(save,
                "{\"0,0\":[{\"centerX\":0,\"centerY\":0,\"centerZ\":0," +
                        "\"floorPositions\":[[1,2,3],[1,[2,3],4]]}]}");

        CaveVoidRegistry reg = newRegistry(save);
        assertDoesNotThrow(reg::load, "load() must survive malformed JSON");
        assertEquals(0, reg.getCount(), "registry should be empty after corrupt load");
        assertFalse(Files.exists(save), "corrupt file should have been moved aside");

        try (var stream = Files.list(tmp)) {
            boolean backedUp = stream.anyMatch(p -> p.getFileName().toString()
                    .startsWith("cave_voids.json.corrupt-"));
            assertTrue(backedUp, "corrupt file should be renamed to .corrupt-<ts>");
        }
    }

    /**
     * Stress test for the save-vs-merge race.
     *
     * <p>A scanner thread repeatedly merges a small number of new floor
     * positions into a seed record, while the main thread repeatedly takes
     * the registry's snapshot and serialises it. Without the deep-snapshot
     * fix, Gson iterates floorPositions while addAll() is running on the
     * same list and emits malformed JSON (the production crash shape).
     */
    @Test
    void concurrentRegisterAndSaveProducesValidJson(@TempDir Path tmp) throws Exception {
        Path save = tmp.resolve("cave_voids.json");
        CaveVoidRegistry reg = newRegistry(save);
        reg.register(makeRecord(0, 64, 0, 500));

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicBoolean sawCorruption = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(1);

        // Scanner: rate-limited merges into the same cell as the seed.
        // Real scanners run at chunk-event cadence (a few per second); here
        // we sleep 1ms between registers so the test stays bounded and
        // doesn't bury the heap while still exercising the race.
        pool.submit(() -> {
            Random rng = new Random(1);
            try {
                while (!stop.get()) {
                    int cx = rng.nextInt(60);
                    int cz = rng.nextInt(60);
                    reg.register(makeRecord(cx, 64, cz, 10));
                    Thread.sleep(1);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
        int cycles = 0;
        while (System.nanoTime() < deadlineNs && !sawCorruption.get()) {
            try {
                reg.saveAsync().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                sawCorruption.set(true);
                break;
            }
            CaveVoidRegistry loader = newRegistry(save);
            try {
                loader.load();
            } catch (RuntimeException e) {
                sawCorruption.set(true);
                break;
            }
            cycles++;
        }

        stop.set(true);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(cycles > 0, "stress loop should have run at least once");
        assertFalse(sawCorruption.get(),
                "save-vs-merge race produced unparseable JSON after " + cycles +
                        " cycles: the deep-snapshot fix in saveAsync() is missing or ineffective");
    }

    private static CaveVoidRecord makeRecord(int cx, int cy, int cz, int floorCount) {
        int[][] floors = new int[floorCount][];
        for (int i = 0; i < floorCount; i++) {
            floors[i] = new int[]{cx + i, cy, cz};
        }
        return new CaveVoidRecord(
                cx, cy, cz,
                cx - 10, cy - 5, cz - 10,
                cx + 10, cy + 5, cz + 10,
                1000, new java.util.ArrayList<>(List.of(floors)),
                0L);
    }
}
