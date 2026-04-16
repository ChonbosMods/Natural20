package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nullable;
import java.util.*;

public class POIPopulationListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|POI");

    public record SpawnDescriptor(String mobRole, int totalCount, int poiX, int poiY, int poiZ) {
        public static @Nullable SpawnDescriptor parse(String raw) {
            if (raw == null || raw.isEmpty()) return null;
            try {
                String[] parts = raw.split(":");
                String role = parts[0];
                int count = Integer.parseInt(parts[1]);
                String[] coords = parts[2].split(",");
                return new SpawnDescriptor(role, count,
                    Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Write a spawn descriptor into the quest bindings instead of spawning mobs immediately.
     * The actual spawning is deferred to POIProximitySystem when the player approaches.
     */
    public void writeSpawnDescriptor(QuestInstance quest, int poiX, int poiY, int poiZ,
                                      String mobRole, int mobCount) {
        Map<String, String> b = quest.getVariableBindings();
        b.put("poi_spawn_descriptor", mobRole + ":" + mobCount + ":" + poiX + "," + poiY + "," + poiZ);
        b.put("poi_mob_state", "PENDING");
        b.put("poi_mob_uuids", "");
        b.put("poi_detached_uuids", "");
        LOGGER.atInfo().log("POI spawn descriptor set for quest %s: %s x%d | /tp %d %d %d",
            quest.getQuestId(), mobRole, mobCount, poiX, poiY, poiZ);
    }

    /**
     * Spawn a tiered mob group at the given POI position via Nat20MobGroupSpawner.
     * The {@code count} parameter is interpreted as champion count: the spawner always
     * adds exactly 1 boss in addition to the champions. Returns UUIDs of all spawned
     * mobs (champions + boss) for kill-tracking.
     */
    public List<String> spawnMobs(World world, String mobRole, int count,
                                   int poiX, int poiY, int poiZ) {
        List<String> uuids = new ArrayList<>();
        Vector3d anchor = new Vector3d(poiX, poiY + 1.0, poiZ);
        var spawner = Natural20.getInstance().getMobGroupSpawner();
        var result = spawner.spawnGroup(world, mobRole, count, anchor, null, false);
        if (result == null) {
            LOGGER.atWarning().log("POI group spawn failed for role=%s | /tp %d %d %d",
                    mobRole, poiX, poiY, poiZ);
            return uuids;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Ref<EntityStore> ref : result.champions()) {
            String uuid = resolveUuid(store, ref);
            if (uuid != null) uuids.add(uuid);
        }
        if (result.boss() != null) {
            String uuid = resolveUuid(store, result.boss());
            if (uuid != null) uuids.add(uuid);
        }
        LOGGER.atInfo().log("POI spawned group role=%s champions=%d boss=%s | /tp %d %d %d",
                mobRole, result.champions().size(), result.bossDifficulty(), poiX, poiY, poiZ);
        return uuids;
    }

    @Nullable
    private String resolveUuid(Store<EntityStore> store, Ref<EntityStore> ref) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        return npc != null ? npc.getUuid().toString() : null;
    }
}
