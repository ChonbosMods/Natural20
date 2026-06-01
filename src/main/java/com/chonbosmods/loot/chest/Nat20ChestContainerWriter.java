package com.chonbosmods.loot.chest;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

/**
 * Chunk-lookup helper for block placement. Live chest loot injection now happens at
 * chunk-generation time via {@link Nat20ChestLootSystem} (which mutates the
 * {@code ItemContainerBlock} component directly, with no {@code setState}
 * rebroadcast), so this class only retains the chunk accessor used by
 * {@link com.chonbosmods.quest.QuestChestPlacer}.
 */
public final class Nat20ChestContainerWriter {

    private Nat20ChestContainerWriter() {}

    public static WorldChunk getLoadedChunk(World world, int x, int z) {
        long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
        return world.getNonTickingChunk(chunkKey);
    }
}
