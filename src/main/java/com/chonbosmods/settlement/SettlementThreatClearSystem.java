package com.chonbosmods.settlement;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Iterator;
import java.util.Map;
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

    public void recordThreat(Ref<EntityStore> npcRef, Ref<EntityStore> attackerRef) {
        String key = npcRef.hashCode() + ":" + attackerRef.hashCode();
        activeThreats.put(key, new ThreatEntry(npcRef, attackerRef, System.currentTimeMillis()));
    }

    @Override
    public void run() {
        try {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, ThreatEntry>> it = activeThreats.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, ThreatEntry> entry = it.next();
                ThreatEntry threat = entry.getValue();

                if (now - threat.lastThreatTime >= THREAT_DURATION_MS) {
                    it.remove();
                    clearMarkedTarget(threat);
                }
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error in threat clear tick");
        }
    }

    private void clearMarkedTarget(ThreatEntry threat) {
        for (SettlementRecord settlement : registry.getAll().values()) {
            World world = registry.getCachedWorld(settlement.getWorldUUID());
            if (world == null) continue;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    if (!threat.npcRef.isValid()) return;

                    NPCEntity npc = store.getComponent(threat.npcRef, NPCEntity.getComponentType());
                    if (npc != null) {
                        npc.onFlockSetTarget("LockedTargetClose", null);
                        LOGGER.atInfo().log("Cleared threat on NPC (ref %s): returning to normal",
                                threat.npcRef);
                    }
                } catch (Exception e) {
                    // NPC may have been removed, ignore
                }
            });
            return;
        }
    }

    private record ThreatEntry(
            Ref<EntityStore> npcRef,
            Ref<EntityStore> attackerRef,
            long lastThreatTime
    ) {}
}
