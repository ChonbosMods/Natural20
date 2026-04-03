package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.npc.Nat20PlaceNameGenerator;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
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

        LOGGER.atFine().log("Placing settlement at cell " + cellKey +
            " position " + settlementX + ", " + settlementZ);

        // Mark as placed immediately to prevent double-placement
        SettlementRecord record = new SettlementRecord(
            cellKey, worldUUID,
            settlementX, 0, settlementZ,
            SettlementType.TOWN);
        record.setName(Nat20PlaceNameGenerator.generate(cellKey.hashCode()));
        registry.register(record);

        // Place structure and spawn NPCs on world thread
        world.execute(() -> {
            try {
                var store = world.getEntityStore().getStore();

                int groundY = findGroundY(world, settlementX, settlementZ);
                Vector3i blockPos = new Vector3i(settlementX, groundY, settlementZ);

                if (placer.hasPrefab(SettlementType.TOWN)) {
                    placer.place(world, blockPos, SettlementType.TOWN, Rotation.None, store, new Random(seed));
                }

                Vector3d origin = new Vector3d(settlementX, groundY, settlementZ);
                List<NpcRecord> npcRecords = Natural20.getInstance().getNpcManager()
                    .spawnSettlementNpcs(store, world, SettlementType.TOWN, origin, cellKey);

                record.setPosY(groundY);
                record.getNpcs().addAll(npcRecords);
                registry.saveAsync();

                // Generate procedural topic dialogues for the new settlement's NPCs
                Natural20.getInstance().onSettlementCreated(record, world);

                LOGGER.atFine().log("Settlement placed at " + settlementX + ", " + groundY + ", " + settlementZ +
                    " with " + npcRecords.size() + " NPCs");
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to place settlement at cell " + cellKey);
            }
        });
    }

    /**
     * Cooldown: don't re-check the same cell more than once per 30 seconds.
     * Allows re-checking after chunks have unloaded and reloaded.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastCheckTime =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CHECK_COOLDOWN_MS = 30_000;

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
                        // Tier 1: fully intact
                        intact++;
                    } else {
                        // Tier 2: entity exists but lost custom components, reattach
                        boolean ok = Natural20.getInstance().getNpcManager()
                            .reattachNpc(store, npcRef, npc, cellKey);
                        if (ok) {
                            reattached++;
                        } else {
                            // Reattach failed: actually respawn as fallback
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

    private int findGroundY(World world, int x, int z) {
        for (int y = 256; y >= 0; y--) {
            BlockType blockType = world.getBlockType(x, y, z);
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y + 1;
            }
        }
        return 64;
    }
}
