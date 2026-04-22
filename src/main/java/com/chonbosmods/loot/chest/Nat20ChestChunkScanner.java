package com.chonbosmods.loot.chest;

import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.progression.MobScalingConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scans loaded chunks for native chest blocks and pre-injects Nat20 affix loot
 * into each unrolled chest. Runs once per chunk on {@code ChunkPreLoadProcessEvent}
 * so the injected item is already in the block's persisted state by the time any
 * player opens it: first-open sees the item natively with no UI resync.
 *
 * <p>Design trade-off vs mid-open mutation: chunks already on disk from before the
 * feature landed won't have loot. Acceptable — chunks reload only on server restart,
 * and new exploration paints as expected. A future {@code /nat20 rerollchests}
 * admin command can backfill explored areas if needed.
 */
public final class Nat20ChestChunkScanner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestChunkScanner");

    private static final int CHUNK_SIZE = 32;
    private static final int MIN_Y = 0;
    private static final int MAX_Y = 200;

    private final Nat20ChestLootConfig config;
    private final Nat20ChestLootRoller roller;
    private final Nat20ChestRollRegistry registry;
    private final MobScalingConfig scalingConfig;
    private final Nat20ChestLootPicker picker;
    private final AtomicInteger chunkScanCount = new AtomicInteger(0);

    public Nat20ChestChunkScanner(
            Nat20ChestLootConfig config,
            Nat20ChestLootRoller roller,
            Nat20ChestRollRegistry registry,
            MobScalingConfig scalingConfig,
            Nat20ChestLootPicker picker) {
        this.config = config;
        this.roller = roller;
        this.registry = registry;
        this.scalingConfig = scalingConfig;
        this.picker = picker;
    }

    public void onChunkLoad(World world, WorldChunk chunk, int chunkBlockX, int chunkBlockZ) {
        if (chunk == null) return;

        Random rng = ThreadLocalRandom.current();
        int chestsFound = 0;
        int rollsAttempted = 0;

        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                int wx = chunkBlockX + lx;
                int wz = chunkBlockZ + lz;
                for (int y = MIN_Y; y < MAX_Y; y++) {
                    int blockId = chunk.getBlock(wx, y, wz);
                    if (blockId <= 0) continue;
                    BlockType bt = (BlockType) BlockType.getAssetMap().getAsset(blockId);
                    if (bt == null) continue;
                    String id = bt.getId();
                    if (id == null || !config.isChestBlock(id)) continue;

                    chestsFound++;
                    LOGGER.atInfo().log("Found chest at %d %d %d type=%s (tp: /tp @s %d %d %d)",
                            wx, y, wz, id, wx, y, wz);

                    if (registry.hasBeenRolled(wx, y, wz)) continue;
                    registry.markRolled(wx, y, wz);
                    rollsAttempted++;

                    if (!roller.roll(rng)) continue;

                    double dist = Math.hypot(wx, wz);
                    int areaLevel = scalingConfig.areaLevelForDistance(dist);

                    Optional<Nat20LootData> loot = picker.pickLoot(areaLevel, rng);
                    if (loot.isEmpty()) continue;

                    Nat20LootData data = loot.get();
                    ItemStack stack = buildItemStack(data);
                    if (stack == null) continue;

                    boolean injected = Nat20ChestContainerWriter.injectIntoFirstEmptySlot(
                            world, wx, y, wz, stack);
                    if (injected) {
                        LOGGER.atInfo().log("Pre-injected chest loot at %d,%d,%d areaLevel=%d -> %s",
                                wx, y, wz, areaLevel, data.getGeneratedName());
                    }
                }
            }
        }

        int scan = chunkScanCount.incrementAndGet();
        if (chestsFound > 0) {
            LOGGER.atInfo().log("Chunk scan (%d,%d): found %d chest(s), %d rolled (scan #%d)",
                    chunkBlockX >> 5, chunkBlockZ >> 5, chestsFound, rollsAttempted, scan);
        } else if (scan % 50 == 1) {
            LOGGER.atInfo().log("Chunk scan (%d,%d): no chests (scan #%d heartbeat)",
                    chunkBlockX >> 5, chunkBlockZ >> 5, scan);
        }
    }

    private static ItemStack buildItemStack(Nat20LootData data) {
        String stackItemId = data.getUniqueItemId();
        if (stackItemId == null || stackItemId.isEmpty()) {
            stackItemId = data.getVariantItemId();
        }
        if (stackItemId == null || stackItemId.isEmpty()) {
            LOGGER.atWarning().log("Chest loot %s has no uniqueItemId or variantItemId; cannot build ItemStack",
                    data.getGeneratedName());
            return null;
        }
        try {
            return new ItemStack(stackItemId, 1).withMetadata(Nat20LootData.METADATA_KEY, data);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to build chest ItemStack for itemId=%s", stackItemId);
            return null;
        }
    }
}
