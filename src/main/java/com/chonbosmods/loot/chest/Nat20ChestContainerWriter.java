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

/**
 * Mutates the container of an already-placed block (chests etc.). Use this for
 * post-placement loot injection (e.g. affix rolls when a native chest is first
 * opened). To CREATE a fresh chest with pre-loaded contents, use
 * {@link com.chonbosmods.quest.QuestChestPlacer} instead: that path builds a
 * block holder from JSON and does a 2-pass hydration, which is not what this
 * class does.
 *
 * <p>All public methods must be called from the world thread.
 */
public final class Nat20ChestContainerWriter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestContainerWriter");

    private Nat20ChestContainerWriter() {}

    public static WorldChunk getLoadedChunk(World world, int x, int z) {
        long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
        return world.getNonTickingChunk(chunkKey);
    }

    /**
     * Clamp every non-stackable item in the chest (tools, armor, hold-weapons) to
     * quantity 1. Vanilla Hytale chest pre-population sometimes hands out stacks of
     * pickaxes / tools that should never stack — when we open such a chest, we
     * clamp first so the player doesn't end up with a phantom duplicate.
     *
     * <p>Returns the number of stacks that were modified (0 if nothing changed).
     * Caller does not need to wrap in another setState — this method does it once
     * if any slot was rewritten.
     */
    public static int clampNonStackableQuantities(World world, int x, int y, int z) {
        WorldChunk chunk = getLoadedChunk(world, x, z);
        if (chunk == null) return 0;

        Holder<ChunkStore> holder = chunk.getBlockComponentHolder(x, y, z);
        if (holder == null) return 0;

        ItemContainerBlock containerBlock = holder.getComponent(ItemContainerBlock.getComponentType());
        if (containerBlock == null) return 0;

        SimpleItemContainer container = containerBlock.getItemContainer();
        if (container == null) return 0;

        int changed = 0;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack existing = container.getItemStack(slot);
            if (ItemStack.isEmpty(existing)) continue;
            if (existing.getQuantity() <= 1) continue;
            if (!shouldBeNonStackable(existing.getItemId())) continue;
            container.setItemStackForSlot(slot, existing.withQuantity(1));
            changed++;
            LOGGER.atInfo().log("Clamped non-stackable %s x%d -> x1 in chest at %d, %d, %d slot %d",
                    existing.getItemId(), existing.getQuantity(), x, y, z, slot);
        }

        if (changed > 0) {
            int blockId = chunk.getBlock(x, y, z);
            BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
            if (blockType != null) {
                int rotationIndex = chunk.getRotationIndex(x, y, z);
                chunk.setState(x, y, z, blockType, rotationIndex, holder);
            }
        }
        return changed;
    }

    /** Tools, armor, and held weapons should never come in stacks. Arrows/bolts stack. */
    private static boolean shouldBeNonStackable(String itemId) {
        if (itemId == null) return false;
        if (itemId.startsWith("Tool_")) return true;
        if (itemId.startsWith("Armor_")) return true;
        if (itemId.startsWith("Weapon_")) {
            return !itemId.startsWith("Weapon_Arrow_") && !itemId.startsWith("Weapon_Bolt_");
        }
        return false;
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
