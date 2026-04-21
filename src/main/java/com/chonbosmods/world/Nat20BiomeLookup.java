package com.chonbosmods.world;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult;
import com.hypixel.hytale.server.worldgen.zone.Zone;

import javax.annotation.Nullable;

public final class Nat20BiomeLookup {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Biome");

    private Nat20BiomeLookup() {}

    @Nullable
    public static String getZoneName(World world, double x, double z) {
        ZoneBiomeResult result = queryResult(world, x, z);
        if (result == null || result.getZoneResult() == null) return null;
        Zone zone = result.getZoneResult().getZone();
        return zone == null ? null : zone.name();
    }

    @Nullable
    public static String getBiomeName(World world, double x, double z) {
        ZoneBiomeResult result = queryResult(world, x, z);
        Biome biome = result == null ? null : result.getBiome();
        return biome == null ? null : biome.getName();
    }

    public record ZoneAndBiome(@Nullable String zone, @Nullable String biome) {
        public static final ZoneAndBiome EMPTY = new ZoneAndBiome(null, null);
    }

    /** Combined lookup: avoids paying the ZoneBiomeResult query twice per call site. */
    public static ZoneAndBiome getZoneAndBiome(World world, double x, double z) {
        ZoneBiomeResult result = queryResult(world, x, z);
        if (result == null) return ZoneAndBiome.EMPTY;
        Zone zone = result.getZoneResult() == null ? null : result.getZoneResult().getZone();
        Biome biome = result.getBiome();
        return new ZoneAndBiome(
            zone == null ? null : zone.name(),
            biome == null ? null : biome.getName()
        );
    }

    @Nullable
    private static ZoneBiomeResult queryResult(World world, double x, double z) {
        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (!(worldGen instanceof ChunkGenerator generator)) return null;
        try {
            int seed = (int) world.getWorldConfig().getSeed();
            return generator.getZoneBiomeResultAt(seed, (int) x, (int) z);
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("zone/biome lookup failed at (%.1f, %.1f)", x, z);
            return null;
        }
    }
}
