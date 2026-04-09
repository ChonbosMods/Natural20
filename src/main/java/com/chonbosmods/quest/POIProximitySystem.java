package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class POIProximitySystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|POIProx");
    private static final double ENTER_RADIUS = 48.0;
    private static final double ENTER_RADIUS_SQ = ENTER_RADIUS * ENTER_RADIUS;
    private static final double EXIT_RADIUS = 64.0;
    private static final double EXIT_RADIUS_SQ = EXIT_RADIUS * EXIT_RADIUS;
    private static final double LEASH_RADIUS = 32.0;
    private static final double LEASH_RADIUS_SQ = LEASH_RADIUS * LEASH_RADIUS;

    /** Tracked online player UUIDs: populated by PlayerReadyEvent, removed by PlayerDisconnectEvent. */
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();

    public void addPlayer(UUID uuid) {
        trackedPlayers.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        trackedPlayers.remove(uuid);
    }

    public Set<UUID> getTrackedPlayers() {
        return Collections.unmodifiableSet(trackedPlayers);
    }

    /**
     * Called every second from a scheduled executor, dispatched to world.execute().
     * Checks all online players with active POI quests and drives state transitions.
     */
    public void tick(World world) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Store<EntityStore> store = world.getEntityStore().getStore();

        for (UUID playerUuid : trackedPlayers) {
            Ref<EntityStore> entityRef = world.getEntityRef(playerUuid);
            if (entityRef == null) continue;

            Nat20PlayerData playerData = store.getComponent(entityRef, Natural20.getPlayerDataType());
            if (playerData == null) continue;

            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) continue;
            double px = transform.getPosition().getX();
            double pz = transform.getPosition().getZ();

            Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
            boolean dirty = false;

            for (QuestInstance quest : quests.values()) {
                Map<String, String> b = quest.getVariableBindings();
                String state = b.get("poi_mob_state");
                if (state == null) continue;

                String rawPoiX = b.get("poi_x");
                String rawPoiZ = b.get("poi_z");
                if (rawPoiX == null || rawPoiZ == null) continue;

                double poiX, poiZ;
                try {
                    poiX = Double.parseDouble(rawPoiX);
                    poiZ = Double.parseDouble(rawPoiZ);
                } catch (NumberFormatException e) { continue; }

                double dx = px - poiX;
                double dz = pz - poiZ;
                double distSq = dx * dx + dz * dz;

                switch (state) {
                    case "PENDING" -> {
                        if (distSq <= ENTER_RADIUS_SQ) {
                            dirty |= transitionToActive(world, store, quest, b);
                        }
                    }
                    case "ACTIVE" -> {
                        if (distSq > EXIT_RADIUS_SQ) {
                            transitionToDetached(b);
                            dirty = true;
                        }
                    }
                    case "DETACHED" -> {
                        if (distSq <= ENTER_RADIUS_SQ) {
                            dirty |= transitionFromDetached(world, store, quest, b);
                        }
                    }
                }
            }

            if (dirty) {
                questSystem.getStateManager().saveActiveQuests(playerData, quests);
            }
        }
    }

    private boolean transitionToActive(World world, Store<EntityStore> store,
                                        QuestInstance quest, Map<String, String> b) {
        boolean anySpawned = false;

        // Spawn quest chest only when the current objective is actually FETCH_ITEM.
        // fetch_item_type lives in global bindings so it persists across phases:
        // without the type guard a KILL_MOBS phase would incorrectly place a chest.
        ObjectiveInstance currentObj = quest.getCurrentObjective();
        String fetchItemType = b.get("fetch_item_type");
        if (fetchItemType != null
                && currentObj != null && currentObj.getType() == ObjectiveType.FETCH_ITEM
                && !"true".equals(b.get("poi_chest_placed"))) {
            try {
                int poiX = (int) Double.parseDouble(b.get("poi_x"));
                int poiY = (int) Double.parseDouble(b.get("poi_y"));
                int poiZ = (int) Double.parseDouble(b.get("poi_z"));
                boolean placed = QuestChestPlacer.placeQuestChest(world, poiX, poiY, poiZ,
                    fetchItemType, b.getOrDefault("fetch_item_label", "quest item"));
                if (placed) {
                    b.put("poi_chest_placed", "true");
                    anySpawned = true;
                }
            } catch (NumberFormatException e) {
                LOGGER.atWarning().log("Bad POI coordinates for FETCH_ITEM quest %s", quest.getQuestId());
            }
        }

        // Spawn hostile mobs (KILL_MOBS or hostile FETCH_ITEM variant)
        POIPopulationListener.SpawnDescriptor desc =
            POIPopulationListener.SpawnDescriptor.parse(b.get("poi_spawn_descriptor"));
        if (desc != null) {
            int credited = getKillProgress(quest);
            int toSpawn = Math.max(0, desc.totalCount() - credited);
            if (toSpawn > 0) {
                List<String> uuids = Natural20.getInstance().getPOIPopulationListener()
                    .spawnMobs(world, desc.mobRole(), toSpawn, desc.poiX(), desc.poiY(), desc.poiZ());
                b.put("poi_mob_uuids", String.join(",", uuids));
                anySpawned = true;
                LOGGER.atInfo().log("PENDING->ACTIVE: quest %s, spawned %d/%d mobs | /tp %d %d %d",
                    quest.getQuestId(), uuids.size(), toSpawn, desc.poiX(), desc.poiY(), desc.poiZ());
            }
        }

        if (anySpawned) {
            b.put("poi_mob_state", "ACTIVE");
            b.remove("poi_detached_uuids");
        }

        return anySpawned;
    }

    private void transitionToDetached(Map<String, String> b) {
        String uuids = b.getOrDefault("poi_mob_uuids", "");
        b.put("poi_detached_uuids", uuids);
        b.put("poi_mob_uuids", "");
        b.put("poi_mob_state", "DETACHED");
        LOGGER.atFine().log("ACTIVE->DETACHED");
    }

    private boolean transitionFromDetached(World world, Store<EntityStore> store,
                                            QuestInstance quest, Map<String, String> b) {
        POIPopulationListener.SpawnDescriptor desc =
            POIPopulationListener.SpawnDescriptor.parse(b.get("poi_spawn_descriptor"));
        if (desc == null) return false;

        String detachedRaw = b.getOrDefault("poi_detached_uuids", "");
        List<String> rebound = new ArrayList<>();

        if (!detachedRaw.isEmpty()) {
            for (String uuid : detachedRaw.split(",")) {
                if (uuid.isEmpty()) continue;
                try {
                    Ref<EntityStore> mobRef = world.getEntityRef(UUID.fromString(uuid));
                    if (mobRef != null) {
                        TransformComponent mobTransform = store.getComponent(mobRef,
                            TransformComponent.getComponentType());
                        if (mobTransform != null) {
                            double mx = mobTransform.getPosition().getX();
                            double mz = mobTransform.getPosition().getZ();
                            double ddx = mx - desc.poiX();
                            double ddz = mz - desc.poiZ();
                            if (ddx * ddx + ddz * ddz <= LEASH_RADIUS_SQ) {
                                rebound.add(uuid);
                                continue;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        int credited = getKillProgress(quest);
        int expected = desc.totalCount() - credited;
        int toSpawn = Math.max(0, expected - rebound.size());

        List<String> spawned = Collections.emptyList();
        if (toSpawn > 0) {
            spawned = Natural20.getInstance().getPOIPopulationListener()
                .spawnMobs(world, desc.mobRole(), toSpawn, desc.poiX(), desc.poiY(), desc.poiZ());
        }

        List<String> allUuids = new ArrayList<>(rebound);
        allUuids.addAll(spawned);

        b.put("poi_mob_uuids", String.join(",", allUuids));
        b.put("poi_mob_state", "ACTIVE");
        b.remove("poi_detached_uuids");

        LOGGER.atInfo().log("DETACHED->ACTIVE: quest %s, rebound %d + spawned %d | /tp %d %d %d",
            quest.getQuestId(), rebound.size(), spawned.size(),
            desc.poiX(), desc.poiY(), desc.poiZ());
        return true;
    }

    private int getKillProgress(QuestInstance quest) {
        ObjectiveInstance obj = quest.getCurrentObjective();
        if (obj != null && obj.getType() == ObjectiveType.KILL_MOBS
                && obj.getLocationId() != null && obj.getLocationId().startsWith("poi:")) {
            return obj.getCurrentCount();
        }
        return 0;
    }
}
