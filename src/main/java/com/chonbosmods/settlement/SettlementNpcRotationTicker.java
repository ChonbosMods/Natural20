package com.chonbosmods.settlement;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled at 100ms intervals to rotate settlement NPCs toward the nearest player
 * within 8 blocks. Creates the "living NPC" feel where NPCs turn their bodies toward
 * approaching players.
 *
 * <p>Uses the world cache in {@link SettlementRegistry} to resolve name-derived world
 * UUIDs to actual World references. Player positions are obtained via
 * {@code World.getPlayerRefs()} and {@code PlayerRef.getTransform()}.
 */
public class SettlementNpcRotationTicker implements Runnable {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Rotation");

    /** 8 blocks squared: NPCs only rotate toward players within this range. */
    private static final double WATCH_RANGE_SQ = 64.0;

    private final SettlementRegistry registry;

    public SettlementNpcRotationTicker(SettlementRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run() {
        try {
            // Group settlements by world UUID so we dispatch one world.execute() per world
            Map<UUID, List<SettlementRecord>> byWorld = new HashMap<>();
            for (SettlementRecord settlement : registry.getAll().values()) {
                UUID worldUUID = settlement.getWorldUUID();
                if (worldUUID == null) continue;
                byWorld.computeIfAbsent(worldUUID, k -> new java.util.ArrayList<>()).add(settlement);
            }

            for (Map.Entry<UUID, List<SettlementRecord>> entry : byWorld.entrySet()) {
                World world = registry.getCachedWorld(entry.getKey());
                if (world == null) continue;

                Collection<PlayerRef> playerRefs = world.getPlayerRefs();
                if (playerRefs.isEmpty()) continue;

                world.execute(() -> {
                    try {
                        Store<EntityStore> store = world.getEntityStore().getStore();

                        for (SettlementRecord settlement : entry.getValue()) {
                            for (NpcRecord npcRecord : settlement.getNpcs()) {
                                rotateNpcToNearestPlayer(world, store, npcRecord, playerRefs);
                            }
                        }
                    } catch (Exception e) {
                        // Silently skip: world may have been unloaded
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error in rotation ticker");
        }
    }

    private void rotateNpcToNearestPlayer(World world, Store<EntityStore> store,
                                           NpcRecord npcRecord,
                                           Collection<PlayerRef> playerRefs) {
        UUID entityUUID = npcRecord.getEntityUUID();
        if (entityUUID == null) return;

        Ref<EntityStore> npcRef = world.getEntityRef(entityUUID);
        if (npcRef == null || !npcRef.isValid()) return;

        TransformComponent npcTransform = store.getComponent(npcRef,
                TransformComponent.getComponentType());
        if (npcTransform == null) return;

        Vector3d npcPos = npcTransform.getPosition();

        // Find nearest player within watch range
        double bestDistSq = WATCH_RANGE_SQ;
        PlayerRef nearest = null;

        for (PlayerRef playerRef : playerRefs) {
            Vector3d playerPos = playerRef.getTransform().getPosition();
            double dx = playerPos.getX() - npcPos.getX();
            double dz = playerPos.getZ() - npcPos.getZ();
            double distSq = dx * dx + dz * dz;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = playerRef;
            }
        }

        if (nearest == null) return;

        // Compute yaw: atan2(dx, dz) + PI to face toward the player
        // This matches the HyCitizens rotation formula
        Vector3d playerPos = nearest.getTransform().getPosition();
        double dx = playerPos.getX() - npcPos.getX();
        double dz = playerPos.getZ() - npcPos.getZ();
        float yaw = (float) (Math.atan2(dx, dz) + Math.PI);

        npcTransform.getRotation().assign(new Vector3f(0, yaw, 0));
    }
}
