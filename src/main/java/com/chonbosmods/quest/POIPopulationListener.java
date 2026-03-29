package com.chonbosmods.quest;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

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
     * Spawn mobs at the given POI position. Extracted for use by the proximity system.
     */
    public List<String> spawnMobs(World world, String mobRole, int count,
                                   int poiX, int poiY, int poiZ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<String> uuids = new ArrayList<>();
        Random rng = new Random();

        int roleIndex = NPCPlugin.get().getIndex(mobRole);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("POI spawn: unknown role '%s'", mobRole);
            return uuids;
        }

        for (int i = 0; i < count; i++) {
            // Small spread (±1.5 blocks) to avoid spawning into dungeon walls.
            // The entrance position itself is guaranteed to be air.
            double offsetX = (rng.nextDouble() - 0.5) * 3;
            double offsetZ = (rng.nextDouble() - 0.5) * 3;
            Vector3d spawnPos = new Vector3d(poiX + offsetX, poiY + 1.0, poiZ + offsetZ);
            Vector3f rotation = new Vector3f(0, (float)(rng.nextDouble() * 2 - 1), 0);

            var result = NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, null, null);
            if (result != null) {
                uuids.add(result.second().getUuid().toString());
            }
        }

        LOGGER.atInfo().log("POI spawned %d %s | /tp %d %d %d", uuids.size(), mobRole, poiX, poiY, poiZ);
        return uuids;
    }
}
