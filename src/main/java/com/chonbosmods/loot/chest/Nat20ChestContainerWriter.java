package com.chonbosmods.loot.chest;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class Nat20ChestContainerWriter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestContainerWriter");

    private Nat20ChestContainerWriter() {}

    public static WorldChunk getLoadedChunk(World world, int x, int z) {
        long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
        return world.getNonTickingChunk(chunkKey);
    }

    public static boolean injectIntoFirstEmptySlot(World world, int x, int y, int z, ItemStack stack) {
        WorldChunk chunk = getLoadedChunk(world, x, z);
        if (chunk == null) {
            LOGGER.atWarning().log("Chunk not loaded at %d, %d for chest inject", x, z);
            return false;
        }

        Holder<ChunkStore> holder = chunk.getBlockComponentHolder(x, y, z);
        if (holder == null) {
            return false;
        }

        ItemContainerBlock containerBlock = holder.getComponent(ItemContainerBlock.getComponentType());
        if (containerBlock == null) {
            return false;
        }

        SimpleItemContainer container = containerBlock.getItemContainer();
        if (container == null) {
            return false;
        }

        short capacity = container.getCapacity();
        short emptySlot = -1;
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack existing = container.getItemStack(slot);
            if (ItemStack.isEmpty(existing)) {
                emptySlot = slot;
                break;
            }
        }
        if (emptySlot < 0) {
            return false;
        }

        container.setItemStackForSlot(emptySlot, stack);

        int blockId = chunk.getBlock(x, y, z);
        BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            LOGGER.atWarning().log("Failed to resolve block type for id %d at %d, %d, %d", blockId, x, y, z);
            return false;
        }
        int rotationIndex = chunk.getRotationIndex(x, y, z);
        chunk.setState(x, y, z, blockType, rotationIndex, holder);

        LOGGER.atInfo().log("Injected %s into chest at %d, %d, %d slot %d",
                stack.getItemId(), x, y, z, emptySlot);
        return true;
    }
}
