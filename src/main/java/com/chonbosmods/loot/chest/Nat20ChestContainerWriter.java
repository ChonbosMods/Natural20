package com.chonbosmods.loot.chest;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Helpers for chest block access + on-open loot injection. The injection path mutates the
 * {@code ItemContainerBlock} component directly and marks the block for saving, with NO
 * {@code chunk.setState(...)} call: the full block-state rebroadcast that {@code setState}
 * performs is what caused the "invalid item" render glitch (handoff Theory 2b). Injecting on
 * the first of a double-tap open (before any container window is built for this press) lets
 * the second press open a settled container, the same way the engine's StashSystem populates
 * a chest before it is ever viewed.
 *
 * <p>All methods must be called from the world thread.
 */
public final class Nat20ChestContainerWriter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestContainerWriter");

    private Nat20ChestContainerWriter() {}

    public static WorldChunk getLoadedChunk(World world, int x, int z) {
        long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
        return world.getNonTickingChunk(chunkKey);
    }

    /**
     * Write {@code stack} into the chest's first empty slot by mutating the
     * {@link ItemContainerBlock} component in place and marking the block for saving. No
     * {@code setState} rebroadcast: the container window built on the next open reads the
     * mutated component directly.
     *
     * @return true if the stack was placed.
     */
    public static boolean injectIntoFirstEmptySlot(World world, int x, int y, int z, ItemStack stack) {
        WorldChunk chunk = getLoadedChunk(world, x, z);
        if (chunk == null) {
            LOGGER.atWarning().log("Chunk not loaded at %d, %d for chest inject", x, z);
            return false;
        }

        Holder<ChunkStore> holder = chunk.getBlockComponentHolder(x, y, z);
        if (holder == null) return false;

        ItemContainerBlock containerBlock = holder.getComponent(ItemContainerBlock.getComponentType());
        if (containerBlock == null) return false;

        SimpleItemContainer container = containerBlock.getItemContainer();
        if (container == null) return false;

        short capacity = container.getCapacity();
        short emptySlot = -1;
        for (short slot = 0; slot < capacity; slot++) {
            if (ItemStack.isEmpty(container.getItemStack(slot))) {
                emptySlot = slot;
                break;
            }
        }
        if (emptySlot < 0) return false;

        container.setItemStackForSlot(emptySlot, stack);

        BlockModule.BlockStateInfo info = holder.getComponent(BlockModule.BlockStateInfo.getComponentType());
        if (info != null) info.markNeedsSaving();

        LOGGER.atInfo().log("Injected %s into chest at %d, %d, %d slot %d", stack.getItemId(), x, y, z, emptySlot);
        return true;
    }
}
