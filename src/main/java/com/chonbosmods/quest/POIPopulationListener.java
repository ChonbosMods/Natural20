package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class POIPopulationListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|POI");
    private static final int TRIGGER_RADIUS_CHUNKS = 4; // ~128 blocks

    public record PendingPopulation(
        String questId,
        int poiX, int poiY, int poiZ,
        String mobRole, int mobCount,
        Ref<EntityStore> playerRef
    ) {}

    private final Map<String, PendingPopulation> pending = new ConcurrentHashMap<>();

    public void register(PendingPopulation pop) {
        pending.put(pop.questId(), pop);
        LOGGER.atInfo().log("Registered pending population for quest %s at (%d, %d, %d): %s x%d",
            pop.questId(), pop.poiX(), pop.poiY(), pop.poiZ(), pop.mobRole(), pop.mobCount());
    }

    /**
     * Spawn mobs immediately at the given position. Called after prefab placement
     * when chunks are guaranteed loaded.
     */
    public void populateNow(World world, QuestInstance quest, Ref<EntityStore> playerRef,
                             int poiX, int poiY, int poiZ, String mobRole, int mobCount) {
        String questId = quest != null ? quest.getQuestId() : "test";
        PendingPopulation pop = new PendingPopulation(
            questId, poiX, poiY, poiZ, mobRole, mobCount, playerRef);
        populate(world, pop);
    }

    public void onChunkLoad(ChunkPreLoadProcessEvent event) {
        if (pending.isEmpty()) return;

        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        int chunkBlockX = chunkX * 32;
        int chunkBlockZ = chunkZ * 32;

        for (var entry : pending.entrySet()) {
            PendingPopulation pop = entry.getValue();
            int dx = Math.abs(chunkBlockX - pop.poiX());
            int dz = Math.abs(chunkBlockZ - pop.poiZ());
            if (dx <= TRIGGER_RADIUS_CHUNKS * 32 && dz <= TRIGGER_RADIUS_CHUNKS * 32) {
                // Two-arg remove ensures only one thread wins if adjacent chunks trigger simultaneously
                if (pending.remove(entry.getKey(), pop)) {
                    World world = event.getChunk().getWorld();
                    if (world != null) {
                        world.execute(() -> populate(world, pop));
                    }
                }
            }
        }
    }

    private void populate(World world, PendingPopulation pop) {
        LOGGER.atInfo().log("Populating POI for quest %s: spawning %d %s at (%d, %d, %d)",
            pop.questId(), pop.mobCount(), pop.mobRole(), pop.poiX(), pop.poiY(), pop.poiZ());

        Store<EntityStore> store = world.getEntityStore().getStore();
        List<String> spawnedUUIDs = new ArrayList<>();
        Random rng = new Random();

        int roleIndex = NPCPlugin.get().getIndex(pop.mobRole());
        if (roleIndex < 0) {
            LOGGER.atWarning().log("POI populate: unknown role '%s'", pop.mobRole());
            return;
        }

        for (int i = 0; i < pop.mobCount(); i++) {
            // Offset mobs within the prefab interior (spread around entrance)
            double offsetX = (rng.nextDouble() - 0.5) * 8;
            double offsetZ = (rng.nextDouble() - 0.5) * 8;
            Vector3d spawnPos = new Vector3d(
                pop.poiX() + offsetX,
                pop.poiY() + 1.0,
                pop.poiZ() + offsetZ
            );
            Vector3f rotation = new Vector3f(0, (float)(rng.nextDouble() * 2 - 1), 0);

            Pair<Ref<EntityStore>, NPCEntity> result =
                NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, null, null);

            if (result != null) {
                NPCEntity npcEntity = result.second();
                spawnedUUIDs.add(npcEntity.getUuid().toString());
                LOGGER.atInfo().log("  Spawned %s at (%.0f, %.0f, %.0f) UUID=%s",
                    pop.mobRole(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                    npcEntity.getUuid());
            }
        }

        // Update quest bindings with spawned mob UUIDs
        if (!spawnedUUIDs.isEmpty()) {
            updateQuestBindings(pop, String.join(",", spawnedUUIDs));
        }
    }

    private void updateQuestBindings(PendingPopulation pop, String uuids) {
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        Nat20PlayerData playerData = store.getComponent(pop.playerRef(), Natural20.getPlayerDataType());
        if (playerData == null) {
            LOGGER.atWarning().log("updateQuestBindings: player ref stale for quest %s", pop.questId());
            return;
        }

        QuestStateManager stateManager = Natural20.getInstance().getQuestSystem().getStateManager();
        Map<String, QuestInstance> quests = stateManager.getActiveQuests(playerData);
        QuestInstance quest = quests.get(pop.questId());
        if (quest == null) return;

        quest.getVariableBindings().put("poi_mob_uuids", uuids);
        quest.getVariableBindings().put("poi_populated", "true");
        stateManager.saveActiveQuests(playerData, quests);

        LOGGER.atInfo().log("POI populated for quest %s: %d mobs, UUIDs=%s",
            pop.questId(), uuids.split(",").length, uuids);
    }
}
