package com.chonbosmods.world;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;

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
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
                WorldChunk chunk = world.getNonTickingChunk(chunkKey);
                if (chunk == null) continue;

                FluidSection fs = chunk.getBlockComponentHolder(0, 0, 0)
                    .getComponent(FluidSection.getComponentType());
                if (fs == null) continue;

                int lx = Math.floorMod(x, ChunkUtil.SIZE);
                int lz = Math.floorMod(z, ChunkUtil.SIZE);
                for (int y = minY; y <= maxY; y++) {
                    int fluidId = chunk.getFluidId(lx, y, lz);
                    if (fluidId == 0) continue;
                    Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
                    if (fluid != null && "Fluid_Lava".equals(fluid.getId())) {
                        fs.setFluid(lx, y, lz, 0, (byte) 0);
                    }
                }
            }
        }
    }
}
