package com.chonbosmods.progression.ambient;

import com.chonbosmods.progression.Nat20MobGroupSpawner;
import com.chonbosmods.quest.poi.MobGroupChunkListener;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator for ambient surface group spawns. Owns per-player cooldown state, subscribes
 * to chunk-load events (via the plugin's event registry), delegates anchor finding and spawn
 * to collaborators.
 *
 * <p>Spawn path lands in Task 9. This file only contains the cooldown gate and the chunk-load
 * hook stub until then.
 */
public final class AmbientSpawnSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Ambient");

    private final AmbientSpawnConfig cfg;
    private final AmbientAnchorFinder anchorFinder;
    private final Nat20MobGroupRegistry registry;
    private final Nat20MobGroupSpawner spawner;
    private final MobGroupChunkListener chunkListener;
    private final CooldownMap cooldowns = new CooldownMap();

    public AmbientSpawnSystem(AmbientSpawnConfig cfg,
                              AmbientAnchorFinder anchorFinder,
                              Nat20MobGroupRegistry registry,
                              Nat20MobGroupSpawner spawner,
                              MobGroupChunkListener chunkListener) {
        this.cfg = cfg;
        this.anchorFinder = anchorFinder;
        this.registry = registry;
        this.spawner = spawner;
        this.chunkListener = chunkListener;
    }

    /**
     * Called from the plugin's ChunkPreLoadProcessEvent handler. Rolls once per eligible player
     * whose cooldown has expired and who's within maxDistanceFromPlayer of the chunk center.
     *
     * <p>Implementation lands in Task 9.
     */
    public void onChunkLoad(World world, int chunkBlockX, int chunkBlockZ) {
        // Implemented in Task 9.
    }

    /** Package-private for unit tests. Keyed by player UUID; stores expiryMillis (absolute wall-clock). */
    static final class CooldownMap {
        private final ConcurrentHashMap<UUID, Long> expiry = new ConcurrentHashMap<>();

        boolean canRoll(UUID playerUuid, long nowMillis) {
            Long exp = expiry.get(playerUuid);
            return exp == null || nowMillis >= exp;
        }

        void markRolled(UUID playerUuid, long nowMillis, long cooldownMillis) {
            expiry.put(playerUuid, nowMillis + cooldownMillis);
        }
    }
}
