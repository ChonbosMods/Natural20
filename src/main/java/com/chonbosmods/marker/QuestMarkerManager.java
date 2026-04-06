package com.chonbosmods.marker;

import com.chonbosmods.data.Nat20NpcData.QuestMarkerState;
import com.chonbosmods.settlement.NpcRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages floating quest marker particles above NPCs.
 * Uses custom particle systems based on Hytale's NPC emotion particles:
 * gold "!" (Quest_Available) for quest available, blue "?" (Quest_TurnIn) for turn-in.
 *
 * Marker state is persisted on NpcRecord.markerState so it survives entity UUID
 * changes on chunk reload/NPC respawn. The in-memory activeMarkers map is synced
 * from NpcRecord during applyNpcComponents.
 */
public class QuestMarkerManager {

    public static final QuestMarkerManager INSTANCE = new QuestMarkerManager();

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|QuestMarker");
    private static final float MARKER_Y_OFFSET = 2.2f;
    private static final String PARTICLE_QUEST_AVAILABLE = "Quest_Available";
    private static final String PARTICLE_QUEST_TURN_IN = "Quest_TurnIn";
    private static final int TICK_INTERVAL = 1;

    /** NPC entity UUID -> quest marker state (used by tickMarkers for particle rendering) */
    private final Map<UUID, QuestMarkerState> activeMarkers = new ConcurrentHashMap<>();
    private int tickCounter;

    private QuestMarkerManager() {}

    /**
     * Register or clear quest marker state for an NPC by entity UUID.
     * Priority guard: QUEST_TURN_IN cannot be overwritten by QUEST_AVAILABLE.
     */
    public void syncMarker(UUID npcUuid, QuestMarkerState state) {
        if (state == QuestMarkerState.NONE) {
            activeMarkers.remove(npcUuid);
        } else if (state == QuestMarkerState.QUEST_AVAILABLE
                   && activeMarkers.get(npcUuid) == QuestMarkerState.QUEST_TURN_IN) {
            // Turn-in takes priority over available: ignore
            return;
        } else {
            activeMarkers.put(npcUuid, state);
        }
    }

    /**
     * Re-evaluate an NPC's marker from its persisted NpcRecord.markerState.
     * Call on NPC spawn/chunk reload to sync the in-memory map from persisted state.
     */
    public void syncFromRecord(UUID npcUuid, NpcRecord record) {
        if (record == null) {
            activeMarkers.remove(npcUuid);
            return;
        }
        String persisted = record.getMarkerState();
        if ("QUEST_TURN_IN".equals(persisted)) {
            activeMarkers.put(npcUuid, QuestMarkerState.QUEST_TURN_IN);
        } else if (record.getPreGeneratedQuest() != null) {
            activeMarkers.put(npcUuid, QuestMarkerState.QUEST_AVAILABLE);
        } else {
            activeMarkers.remove(npcUuid);
        }
    }

    /**
     * Re-evaluate an NPC's marker state from its NpcRecord baseline and apply directly.
     * Bypasses the priority guard: call this when the previous state has been consumed
     * (e.g., after turn-in completes) and the NPC needs a fresh evaluation.
     */
    public void evaluateAndApply(UUID npcUuid, NpcRecord record) {
        if (record == null) {
            activeMarkers.remove(npcUuid);
            return;
        }
        if (record.getPreGeneratedQuest() != null) {
            activeMarkers.put(npcUuid, QuestMarkerState.QUEST_AVAILABLE);
        } else {
            activeMarkers.remove(npcUuid);
        }
    }

    /**
     * Spawn particle indicators above tracked NPCs.
     * Called every 1s from the proximity executor; only fires particles every TICK_INTERVAL calls.
     * Skips NPCs in busy states (combat, alerted, interaction).
     */
    public void tickMarkers(World world) {
        if (activeMarkers.isEmpty()) return;

        tickCounter++;
        if (tickCounter % TICK_INTERVAL != 0) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        Iterator<Map.Entry<UUID, QuestMarkerState>> it = activeMarkers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, QuestMarkerState> entry = it.next();
            UUID npcUuid = entry.getKey();
            QuestMarkerState state = entry.getValue();

            Ref<EntityStore> npcRef = world.getEntityRef(npcUuid);
            if (npcRef == null) {
                // NPC entity not loaded (chunk unloaded): skip this tick but keep the marker
                continue;
            }

            // Skip if NPC is busy (combat, alerted, interacting)
            NPCEntity npcEntity = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity != null && npcEntity.getRole() != null
                    && npcEntity.getRole().getStateSupport().isInBusyState()) {
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

    public void clear() {
        activeMarkers.clear();
    }
}
