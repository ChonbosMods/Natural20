package com.chonbosmods.loot;

import com.chonbosmods.loot.registry.Nat20ItemRegistry;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.HytaleServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Nat20ItemGarbageCollector {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final long DEBOUNCE_SECONDS = 5;

    private final Nat20ItemRegistry registry;
    private final Map<String, Long> pendingCleanup = new ConcurrentHashMap<>();

    public Nat20ItemGarbageCollector(Nat20ItemRegistry registry) {
        this.registry = registry;
    }

    public void register(EventRegistry eventRegistry) {
        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                this::processCleanupQueue, 10, 5, TimeUnit.SECONDS);
        LOGGER.atInfo().log("Item GC registered");
    }

    public void onItemDestroyed(String itemId) {
        if (!registry.isNat20Item(itemId)) return;
        pendingCleanup.put(itemId, System.currentTimeMillis() + (DEBOUNCE_SECONDS * 1000));
        LOGGER.atFine().log("Scheduled GC for item: %s", itemId);
    }

    public void cancelCleanup(String itemId) {
        pendingCleanup.remove(itemId);
    }

    private void processCleanupQueue() {
        if (pendingCleanup.isEmpty()) return;
        long now = System.currentTimeMillis();

        var iterator = pendingCleanup.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now >= entry.getValue()) {
                iterator.remove();
                registry.unregisterItem(entry.getKey());
            }
        }
    }
}
