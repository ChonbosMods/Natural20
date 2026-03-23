package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
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

        LOGGER.atInfo().log("Placing settlement at cell " + cellKey +
            " position " + settlementX + ", " + settlementZ);

        // Mark as placed immediately to prevent double-placement
        SettlementRecord record = new SettlementRecord(
            cellKey, worldUUID,
            settlementX, 0, settlementZ,
            SettlementType.TOWN);
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

                LOGGER.atInfo().log("Settlement placed at " + settlementX + ", " + groundY + ", " + settlementZ +
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
     * Check if NPCs for an existing settlement are missing from the entity store
     * and respawn them if needed. Entities can be lost on chunk unload/reload.
     */
    private void checkAndRespawnNpcs(World world, String cellKey) {
        long now = System.currentTimeMillis();
        Long lastCheck = lastCheckTime.get(cellKey);
        if (lastCheck != null && now - lastCheck < CHECK_COOLDOWN_MS) return;
        lastCheckTime.put(cellKey, now);

        SettlementRecord record = registry.getByCell(cellKey);
        if (record == null || record.getNpcs().isEmpty()) return;

        // Defer to world thread for entity store access
        world.execute(() -> {
            var store = world.getEntityStore().getStore();

            List<NpcRecord> missingNpcs = new ArrayList<>();
            for (NpcRecord npc : record.getNpcs()) {
                if (npc.getEntityUUID() == null) continue;
                boolean exists = false;
                try {
                    var npcRef = world.getEntityRef(npc.getEntityUUID());
                    exists = npcRef != null && store.getComponent(npcRef,
                        com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType()) != null;
                } catch (Exception ignored) {}
                if (!exists) {
                    missingNpcs.add(npc);
                }
            }

            if (missingNpcs.isEmpty()) return;

            LOGGER.atInfo().log("Settlement %s: %d/%d NPCs missing from entity store, respawning",
                cellKey, missingNpcs.size(), record.getNpcs().size());

            for (NpcRecord npc : missingNpcs) {
                UUID newUUID = Natural20.getInstance().getNpcManager().respawnNpc(store, world, npc);
                if (newUUID != null) {
                    LOGGER.atInfo().log("  Respawned %s (%s) with new UUID %s",
                        npc.getGeneratedName(), npc.getRole(), newUUID);
                }
            }

            registry.saveAsync();
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
