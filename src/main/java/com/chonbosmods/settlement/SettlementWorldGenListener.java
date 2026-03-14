package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
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

    /** Hytale chunks are 32x32 blocks (not 16). */
    private static final int CHUNK_BLOCK_SIZE = 32;

    private final SettlementRegistry registry;
    private final SettlementPlacer placer;

    public SettlementWorldGenListener(SettlementRegistry registry, SettlementPlacer placer) {
        this.registry = registry;
        this.placer = placer;
    }

    /**
     * Called from ChunkPreLoadProcessEvent handler.
     * Checks if the chunk contains a settlement center and places it if needed.
     *
     * @param world       the world the chunk belongs to
     * @param chunkBlockX the block-coordinate origin of the chunk (chunkX * 32)
     * @param chunkBlockZ the block-coordinate origin of the chunk (chunkZ * 32)
     */
    public void onChunkLoad(World world, int chunkBlockX, int chunkBlockZ) {
        // Determine which grid cell this chunk falls in
        int cellX = Math.floorDiv(chunkBlockX, CELL_SIZE);
        int cellZ = Math.floorDiv(chunkBlockZ, CELL_SIZE);
        String cellKey = cellX + "," + cellZ;

        // Skip if already placed
        if (registry.hasCell(cellKey)) {
            return;
        }

        // Compute deterministic settlement position for this cell
        long seed = (long) cellX * 341873128712L + (long) cellZ * 132897987541L + SEED_OFFSET;
        Random rng = new Random(seed);

        int cellOriginX = cellX * CELL_SIZE;
        int cellOriginZ = cellZ * CELL_SIZE;
        int settlementX = cellOriginX + (int) (CELL_SIZE * (JITTER_MIN + rng.nextDouble() * (JITTER_MAX - JITTER_MIN)));
        int settlementZ = cellOriginZ + (int) (CELL_SIZE * (JITTER_MIN + rng.nextDouble() * (JITTER_MAX - JITTER_MIN)));

        // Check if THIS chunk (32x32 blocks) contains the settlement center
        if (chunkBlockX > settlementX || settlementX >= chunkBlockX + CHUNK_BLOCK_SIZE ||
            chunkBlockZ > settlementZ || settlementZ >= chunkBlockZ + CHUNK_BLOCK_SIZE) {
            return;
        }

        LOGGER.atInfo().log("Placing settlement at cell " + cellKey +
            " position " + settlementX + ", " + settlementZ);

        // Mark as placed immediately to prevent double-placement
        SettlementRecord record = new SettlementRecord(
            cellKey, UUID.nameUUIDFromBytes(world.getName().getBytes()),
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

                record.getNpcs().addAll(npcRecords);
                registry.saveAsync();

                LOGGER.atInfo().log("Settlement placed at " + settlementX + ", " + groundY + ", " + settlementZ +
                    " with " + npcRecords.size() + " NPCs");
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to place settlement at cell " + cellKey);
            }
        });
    }

    private int findGroundY(World world, int x, int z) {
        for (int y = 128; y >= 0; y--) {
            BlockType blockType = world.getBlockType(x, y, z);
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y + 1;
            }
        }
        return 64;
    }
}
