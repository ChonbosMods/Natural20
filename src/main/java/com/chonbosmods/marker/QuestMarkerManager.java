package com.chonbosmods.marker;

import com.chonbosmods.data.Nat20NpcData.QuestMarkerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages floating quest marker particles above NPCs.
 * Uses Hytale's built-in NPC emotion particles:
 * "Alerted" (!) for quest available, "Question" (?) for quest turn-in.
 *
 * Particles are fire-and-forget with short lifespans (1s and 3s respectively),
 * so they're re-spawned on each tick (~1Hz) to maintain visibility.
 */
public class QuestMarkerManager {

    public static final QuestMarkerManager INSTANCE = new QuestMarkerManager();

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|QuestMarker");
    private static final float MARKER_Y_OFFSET = 2.0f;
    private static final String PARTICLE_QUEST_AVAILABLE = "Alerted";
    private static final String PARTICLE_QUEST_TURN_IN = "Question";

    /** NPC entity UUID → quest marker state */
    private final Map<UUID, QuestMarkerState> activeMarkers = new ConcurrentHashMap<>();

    private QuestMarkerManager() {}

    /**
     * Register or clear quest marker state for an NPC.
     * Called from applyNpcComponents and updateSettlementNameplates.
     */
    public void syncMarker(UUID npcUuid, QuestMarkerState state) {
        if (state == QuestMarkerState.NONE) {
            activeMarkers.remove(npcUuid);
        } else {
            activeMarkers.put(npcUuid, state);
        }
    }

    /**
     * Spawn particle indicators above all tracked NPCs.
     * Call periodically from the world thread (~1Hz).
     */
    public void tickMarkers(World world) {
        if (activeMarkers.isEmpty()) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        Iterator<Map.Entry<UUID, QuestMarkerState>> it = activeMarkers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, QuestMarkerState> entry = it.next();
            UUID npcUuid = entry.getKey();
            QuestMarkerState state = entry.getValue();

            Ref<EntityStore> npcRef = world.getEntityRef(npcUuid);
            if (npcRef == null) {
                // NPC entity gone (died, chunk unloaded), stop tracking
                it.remove();
                continue;
            }

            TransformComponent transform = store.getComponent(npcRef,
                TransformComponent.getComponentType());
            if (transform == null) continue;

            Vector3d npcPos = transform.getPosition();
            Vector3d particlePos = new Vector3d(
                npcPos.getX(), npcPos.getY() + MARKER_Y_OFFSET, npcPos.getZ());

            String particleId = state == QuestMarkerState.QUEST_AVAILABLE
                ? PARTICLE_QUEST_AVAILABLE : PARTICLE_QUEST_TURN_IN;

            ParticleUtil.spawnParticleEffect(particleId, particlePos, store);
        }
    }

    /**
     * Remove all tracked markers. Call on shutdown.
     */
    public void clear() {
        activeMarkers.clear();
    }
}
