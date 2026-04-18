package com.chonbosmods.world;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.zone.Zone;
import com.hypixel.hytale.server.worldgen.zone.ZonePatternProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Nat20ZoneRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ZoneReg");

    private List<String> zoneNames = List.of();
    private boolean initialized = false;

    public void initialize(World world) {
        if (world == null) {
            LOGGER.atWarning().log("initialize called with null world; zones empty");
            this.initialized = true;
            return;
        }
        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (!(worldGen instanceof ChunkGenerator generator)) {
            LOGGER.atWarning().log("world generator is not ChunkGenerator (type=%s); zones empty",
                worldGen == null ? "null" : worldGen.getClass().getSimpleName());
            this.initialized = true;
            return;
        }

        ZonePatternProvider provider = generator.getZonePatternProvider();
        Zone[] zones = provider.getZones();
        List<String> names = new ArrayList<>(zones.length);
        for (Zone z : zones) {
            names.add(z.name());
        }
        Collections.sort(names);
        this.zoneNames = Collections.unmodifiableList(names);
        this.initialized = true;

        LOGGER.atInfo().log("Zone registry initialized: %d zones", zones.length);
    }

    public List<String> getZoneNames() {
        return zoneNames;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
