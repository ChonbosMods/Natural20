package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.npc.Nat20PlaceNameGenerator;
import com.chonbosmods.world.Nat20HeightmapSampler;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SettlementWorldGenListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|WorldGen");

    private static final int CELL_SIZE = 512;
    private static final double JITTER_MIN = 0.25;
    private static final double JITTER_MAX = 0.75;
    private static final long SEED_OFFSET = 827364510L;

    /**
     * Per-cell probability that the settlement is an OUTPOST instead of a TOWN.
     * 0.0 = always TOWN, 1.0 = always OUTPOST. Rolled deterministically from the
     * per-cell seed so reloading the world produces the same settlement types.
     */
    private static final double OUTPOST_SPAWN_CHANCE = 0.5;

    /** Hytale chunks are 32x32 blocks. */
    private static final int CHUNK_BLOCK_SIZE = 32;

    private final SettlementRegistry registry;
    private final SettlementPlacer placer;

    public SettlementWorldGenListener(SettlementRegistry registry, SettlementPlacer placer) {
        this.registry = registry;
        this.placer = placer;
    }

    public void onChunkLoad(World world, int chunkBlockX, int chunkBlockZ) {
        // Cache world reference for the rotation ticker
        UUID worldUUID = UUID.nameUUIDFromBytes(world.getName().getBytes());
        registry.cacheWorld(worldUUID, world);

        // Determine which grid cell this chunk falls in
        int cellX = Math.floorDiv(chunkBlockX, CELL_SIZE);
        int cellZ = Math.floorDiv(chunkBlockZ, CELL_SIZE);
        String cellKey = cellX + "," + cellZ;

        // Settlement already placed: check if NPCs need respawning
        if (registry.hasCell(cellKey)) {
            checkAndRespawnNpcs(world, cellKey);
            return;
        }

        // Compute deterministic settlement position for this cell
        long seed = (long) cellX * 341873128712L + (long) cellZ * 132897987541L + SEED_OFFSET;
        Random rng = new Random(seed);

        int cellOriginX = cellX * CELL_SIZE;
        int cellOriginZ = cellZ * CELL_SIZE;
        int settlementX = cellOriginX + (int) (CELL_SIZE * (JITTER_MIN + rng.nextDouble() * (JITTER_MAX - JITTER_MIN)));
        int settlementZ = cellOriginZ + (int) (CELL_SIZE * (JITTER_MIN + rng.nextDouble() * (JITTER_MAX - JITTER_MIN)));

        // Only the chunk containing the settlement center triggers placement
        if (!isChunkContaining(chunkBlockX, chunkBlockZ, settlementX, settlementZ)) {
            return;
        }

        // Per-cell type roll: OUTPOST_SPAWN_CHANCE of the time use OUTPOST, else TOWN.
        SettlementType type = rng.nextDouble() < OUTPOST_SPAWN_CHANCE
            ? SettlementType.OUTPOST
            : SettlementType.TOWN;

        LOGGER.atFine().log("Placing %s at cell %s position %d, %d",
            type, cellKey, settlementX, settlementZ);

        // Mark as placed immediately to prevent double-placement
        SettlementRecord record = new SettlementRecord(
            cellKey, worldUUID,
            settlementX, 0, settlementZ,
            type);
        record.setName(Nat20PlaceNameGenerator.generate(cellKey.hashCode(), registry.getUsedNames()));
        registry.register(record);

        // Place structure and spawn NPCs on world thread
        world.execute(() -> {
            var store = world.getEntityStore().getStore();
            int groundY = findGroundY(world, settlementX, settlementZ);
            if (groundY <= 0) {
                // Sampler returned its no-ground sentinel: center is over water, in a
                // cave, or on unloaded chunks. Don't force a placement with bogus Y;
                // drop the tentative record so it doesn't ghost in /nat20 settlements.
                LOGGER.atFine().log(
                    "No valid ground at settlement center (%d, %d) for cell %s; skipping",
                    settlementX, settlementZ, cellKey);
                registry.unregister(cellKey);
                return;
            }
            Vector3i anchorPos = new Vector3i(settlementX, groundY, settlementZ);

            if (!placer.hasPrefab(type)) {
                LOGGER.atSevere().log("No prefab for %s, skipping settlement at cell %s", type, cellKey);
                registry.unregister(cellKey);
                return;
            }

            placer.place(world, anchorPos, type, Rotation.None, store, new Random(seed))
                .whenComplete((placed, error) -> world.execute(() -> {
                    if (error != null || placed == null) {
                        LOGGER.atSevere().withCause(error).log(
                            "Settlement placement failed for cell %s; removing tentative record",
                            cellKey);
                        registry.unregister(cellKey);
                        return;
                    }

                    List<NpcRecord> spawned = SettlementNpcFanOut.spawn(
                        store, world, type, placed.npcSpawnsWorld(),
                        cellKey, record.getPlacedAt());

                    record.setPosY(groundY);
                    record.getNpcs().addAll(spawned);
                    registry.saveAsync();

                    Natural20.getInstance().onSettlementCreated(record, world);
                    LOGGER.atFine().log("Settlement placed at %d, %d, %d with %d NPCs",
                        settlementX, groundY, settlementZ, spawned.size());
                }));
        });
    }

    /**
     * Cooldown: don't re-check the same cell more than once per 30 seconds.
     * Allows re-checking after chunks have unloaded and reloaded.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastCheckTime =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CHECK_COOLDOWN_MS = 500;

    /**
     * Check NPCs for an existing settlement and recover them if needed.
     * 3-tier recovery:
     *   1. Entity exists with Nat20NpcData: intact, skip
     *   2. Entity exists without Nat20NpcData: reattach components (preserves UUID)
     *   3. Entity missing: full respawn (new UUID)
     */
    private void checkAndRespawnNpcs(World world, String cellKey) {
        long now = System.currentTimeMillis();
        Long lastCheck = lastCheckTime.get(cellKey);
        if (lastCheck != null && now - lastCheck < CHECK_COOLDOWN_MS) return;
        lastCheckTime.put(cellKey, now);

        SettlementRecord record = registry.getByCell(cellKey);
        if (record == null || record.getNpcs().isEmpty()) return;

        world.execute(() -> {
            var store = world.getEntityStore().getStore();

            int reattached = 0;
            int respawned = 0;
            int intact = 0;

            for (NpcRecord npc : record.getNpcs()) {
                if (npc.getEntityUUID() == null) {
                    // Dead NPC (UUID cleared by death system): skip
                    continue;
                }

                // Skip reconciliation if the NPC's chunk isn't loaded yet.
                // Prevents duplicate spawns when reconciliation runs before
                // the NPC's chunk has loaded (getEntityRef returns null for
                // entities in unloaded chunks, triggering a false Tier 3 respawn).
                int npcChunkX = (int) Math.floor(npc.getSpawnX()) >> 5;
                int npcChunkZ = (int) Math.floor(npc.getSpawnZ()) >> 5;
                long chunkKey = ((long) npcChunkX << 32) | (npcChunkZ & 0xFFFFFFFFL);
                if (world.getChunkIfLoaded(chunkKey) == null) {
                    continue;
                }

                Ref<EntityStore> npcRef = null;
                boolean entityExists = false;
                try {
                    npcRef = world.getEntityRef(npc.getEntityUUID());
                    entityExists = npcRef != null && store.getComponent(npcRef,
                        com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType()) != null;
                } catch (Exception ignored) {}

                if (entityExists) {
                    // Entity survived: check if custom data is intact
                    Nat20NpcData npcData = store.getComponent(npcRef, Natural20.getNpcDataType());
                    if (npcData != null && npcData.getGeneratedName() != null) {
                        // Tier 1: entity and data intact. Re-apply skin because
                        // chunk reload reconstructs a bare Player model from
                        // PersistentModel, stripping skin/attachments.
                        Natural20.getInstance().getNpcManager()
                            .reattachNpc(store, npcRef, npc, cellKey, world);
                        intact++;
                    } else {
                        // Tier 2: entity exists but lost custom components, reattach
                        boolean ok = Natural20.getInstance().getNpcManager()
                            .reattachNpc(store, npcRef, npc, cellKey, world);
                        if (ok) {
                            reattached++;
                        } else {
                            // Reattach failed: kill the old entity to prevent naked ghost,
                            // then respawn with a new entity
                            npc.setEntityUUID(null);
                            try {
                                store.tryRemoveComponent(npcRef, Invulnerable.getComponentType());
                                EntityStatMap oldStats = store.getComponent(npcRef, EntityStatMap.getComponentType());
                                if (oldStats != null) {
                                    int hi = EntityStatType.getAssetMap().getIndex("Health");
                                    if (hi >= 0) oldStats.minimizeStatValue(hi);
                                }
                            } catch (Exception ignored) {}
                            UUID newUUID = Natural20.getInstance().getNpcManager()
                                .respawnNpc(store, world, npc, cellKey);
                            if (newUUID != null) respawned++;
                        }
                    }
                } else {
                    // Tier 3: entity is gone, full respawn
                    UUID newUUID = Natural20.getInstance().getNpcManager()
                        .respawnNpc(store, world, npc, cellKey);
                    if (newUUID != null) respawned++;
                }
            }

            if (reattached > 0 || respawned > 0) {
                LOGGER.atFine().log("Settlement %s: %d intact, %d reattached, %d respawned",
                    cellKey, intact, reattached, respawned);
                registry.saveAsync();
            }
        });
    }

    /** Check if a block position falls within the given chunk. */
    private boolean isChunkContaining(int chunkBlockX, int chunkBlockZ, int blockX, int blockZ) {
        return chunkBlockX <= blockX && blockX < chunkBlockX + CHUNK_BLOCK_SIZE &&
               chunkBlockZ <= blockZ && blockZ < chunkBlockZ + CHUNK_BLOCK_SIZE;
    }

    /**
     * @return valid ground Y at (x, z), or 0 if the sampler can't find one
     *         (water column, all-transparent, unloaded chunk, etc.). Callers
     *         must handle the 0 sentinel explicitly rather than placing at it.
     */
    private int findGroundY(World world, int x, int z) {
        Nat20HeightmapSampler.SampleResult sample = Nat20HeightmapSampler.sample(
            world, x, z, 0, 0,
            Nat20HeightmapSampler.Mode.ENTRY_ANCHOR,
            Integer.MAX_VALUE);
        return sample.y();
    }
}
