package com.chonbosmods.world;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Clears fluid cells inside a world-coordinate volume. Used post-paste by hostile POI placement
 * to remove lava that the prefab walls now expose: Hytale does not propagate fluid physics on
 * setFluid, so a one-shot sweep is permanent. Only Fluid_Lava is cleared; decorative water is
 * preserved.
 */
public final class Nat20FluidSweeper {

    private Nat20FluidSweeper() {}

    /** Clear every Fluid_Lava cell in the inclusive (worldMin..worldMax) volume.
     *  Must run on the world thread. Tolerates unloaded chunks (skips them). */
    public static void clearLavaInVolume(World world,
                                         int minX, int minY, int minZ,
                                         int maxX, int maxY, int maxZ) {
        // FluidSection is per-section (10 sections of 32 blocks each per chunk), not per-chunk.
        // Mirror WorldChunk.getFluidId's pattern: resolve section via ChunkColumn for each Y.
        // Cache lava id once: avoids 38k hash + string compares per sweep.
        int lavaFluidId = Fluid.getAssetMap().getIndex("Fluid_Lava");
        if (lavaFluidId < 0) return;  // unregistered (getIndex returns Integer.MIN_VALUE)

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
                WorldChunk chunk = world.getNonTickingChunk(chunkKey);
                if (chunk == null) continue;

                Ref<ChunkStore> columnRef = chunk.getReference();
                Store<ChunkStore> store = columnRef.getStore();
                ChunkColumn column = store.getComponent(columnRef, ChunkColumn.getComponentType());
                if (column == null) continue;

                int lx = Math.floorMod(x, ChunkUtil.SIZE);
                int lz = Math.floorMod(z, ChunkUtil.SIZE);

                FluidSection fs = null;
                int currentSection = Integer.MIN_VALUE;
                for (int y = minY; y <= maxY; y++) {
                    int sectionIdx = ChunkUtil.chunkCoordinate(y);
                    if (sectionIdx != currentSection) {
                        currentSection = sectionIdx;
                        Ref<ChunkStore> sectionRef = column.getSection(sectionIdx);
                        fs = (sectionRef == null) ? null
                            : store.getComponent(sectionRef, FluidSection.getComponentType());
                    }
                    if (fs == null) continue;

                    if (fs.getFluidId(lx, y, lz) == lavaFluidId) {
                        fs.setFluid(lx, y, lz, 0, (byte) 0);
                    }
                }
            }
        }
    }
}
