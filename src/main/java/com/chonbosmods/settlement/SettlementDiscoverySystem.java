package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SettlementDiscoverySystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Discovery");

    private static final double DISCOVERY_RADIUS = 48.0;
    private static final double DISCOVERY_RADIUS_SQ = DISCOVERY_RADIUS * DISCOVERY_RADIUS;

    private static final float TITLE_DURATION = 5.0f;
    private static final float TITLE_FADE_IN = 1.0f;
    private static final float TITLE_FADE_OUT = 1.5f;

    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();

    public void addPlayer(UUID uuid) {
        trackedPlayers.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        trackedPlayers.remove(uuid);
    }

    public void tick(World world) {
        SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();
        if (registry == null) return;

        var settlements = registry.getAll();
        if (settlements.isEmpty()) return;

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

            for (SettlementRecord settlement : settlements.values()) {
                String cellKey = settlement.getCellKey();
                if (playerData.hasDiscoveredSettlement(cellKey)) continue;

                double dx = px - settlement.getPosX();
                double dz = pz - settlement.getPosZ();
                double distSq = dx * dx + dz * dz;

                if (distSq <= DISCOVERY_RADIUS_SQ) {
                    playerData.discoverSettlement(cellKey);

                    Player player = store.getComponent(entityRef, Player.getComponentType());
                    if (player != null) {
                        String settlementName = settlement.deriveName();
                        String typeName = settlement.getType();

                        EventTitleUtil.showEventTitleToPlayer(
                                player.getPlayerRef(),
                                Message.raw(settlementName),
                                Message.raw(typeName),
                                true,
                                null,
                                TITLE_DURATION, TITLE_FADE_IN, TITLE_FADE_OUT);

                        LOGGER.atInfo().log("Player %s discovered settlement %s (%s)",
                                playerUuid, settlementName, cellKey);
                    }
                    break; // one discovery per tick per player
                }
            }
        }
    }
}
