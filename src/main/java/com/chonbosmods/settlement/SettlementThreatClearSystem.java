package com.chonbosmods.settlement;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled at 1-second intervals. Tracks NPC-to-attacker threat pairs and clears
 * the marked target after 5 seconds of no new threat events. When cleared, the NPC's
 * behavior tree transitions back to Idle (HasTarget condition fails).
 */
public class SettlementThreatClearSystem implements Runnable {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ThreatClear");

    private static final long THREAT_DURATION_MS = 5000;

    private final ConcurrentHashMap<String, ThreatEntry> activeThreats = new ConcurrentHashMap<>();

    private final SettlementRegistry registry;

    public SettlementThreatClearSystem(SettlementRegistry registry) {
        this.registry = registry;
    }

    public void recordThreat(Ref<EntityStore> npcRef, Ref<EntityStore> attackerRef, UUID worldUUID) {
        String key = npcRef.hashCode() + ":" + attackerRef.hashCode();
        activeThreats.put(key, new ThreatEntry(npcRef, attackerRef, worldUUID, System.currentTimeMillis()));
    }

    @Override
    public void run() {
        try {
            long now = System.currentTimeMillis();
            List<ThreatEntry> expired = new ArrayList<>();

            activeThreats.forEach((key, threat) -> {
                if (now - threat.lastThreatTime >= THREAT_DURATION_MS) {
                    // Atomic conditional remove: only removes if the value hasn't been
                    // replaced by a newer recordThreat() call since we read it
                    if (activeThreats.remove(key, threat)) {
                        expired.add(threat);
                    }
                }
            });

            for (ThreatEntry threat : expired) {
                clearMarkedTarget(threat);
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error in threat clear tick");
        }
    }

    private void clearMarkedTarget(ThreatEntry threat) {
        World world = registry.getCachedWorld(threat.worldUUID);
        if (world == null) return;

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();

                if (!threat.npcRef.isValid()) return;

                NPCEntity npc = store.getComponent(threat.npcRef, NPCEntity.getComponentType());
                if (npc != null) {
                    npc.onFlockSetTarget("LockedTarget", null);
                    LOGGER.atInfo().log("Cleared threat on NPC (ref %s): returning to normal",
                            threat.npcRef);
                }
            } catch (Exception e) {
                // NPC may have been removed, ignore
            }
        });
    }

    private record ThreatEntry(
            Ref<EntityStore> npcRef,
            Ref<EntityStore> attackerRef,
            UUID worldUUID,
            long lastThreatTime
    ) {}
}
