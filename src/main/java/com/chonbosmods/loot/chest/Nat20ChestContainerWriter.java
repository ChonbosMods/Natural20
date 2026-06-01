package com.chonbosmods.loot.chest;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
 * Helpers for chest block access + on-open loot injection. Injection mutates the
 * <b>live</b> {@link ItemContainerBlock} component (reached via the block's ChunkStore ref,
 * the same object the chunk-generation path mutates) and marks the block for saving, with NO
 * {@code chunk.setState(...)} call. The full block-state rebroadcast that {@code setState}
 * performs is what caused the "invalid item" render glitch (handoff Theory 2b).
 *
 * <p>Crucial: {@link WorldChunk#getBlockComponentHolder} returns a <em>clone</em> of the
 * container — mutating it is discarded. {@link WorldChunk#getBlockComponentEntity} returns the
 * live {@link Ref}, and {@code ref.getStore().getComponent(ref, type)} the live component, so
 * the write actually takes effect and the container window built on the next open reflects it.
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
     * Write {@code stack} into the chest's first empty slot by mutating the live
     * {@link ItemContainerBlock} component and marking the block for saving. No
     * {@code setState} rebroadcast: the container window built on the next open reads the
     * mutated live component directly.
     *
     * @return true if the stack was placed.
     */
    public static boolean injectIntoFirstEmptySlot(World world, int x, int y, int z, ItemStack stack) {
        WorldChunk chunk = getLoadedChunk(world, x, z);
        if (chunk == null) {
            LOGGER.atWarning().log("Chunk not loaded at %d, %d for chest inject", x, z);
            return false;
        }

        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
        if (blockRef == null) return false;
        Store<ChunkStore> store = blockRef.getStore();
        if (store == null) return false;

        ItemContainerBlock containerBlock = store.getComponent(blockRef, ItemContainerBlock.getComponentType());
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

        BlockModule.BlockStateInfo info = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) info.markNeedsSaving();

        LOGGER.atFine().log("Injected %s into chest at %d, %d, %d slot %d", stack.getItemId(), x, y, z, emptySlot);
        return true;
    }
}
