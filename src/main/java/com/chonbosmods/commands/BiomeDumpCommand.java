package com.chonbosmods.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.biome.BiomePatternGenerator;
import com.hypixel.hytale.server.worldgen.biome.CustomBiome;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.zone.Zone;
import com.hypixel.hytale.server.worldgen.zone.ZonePatternProvider;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BiomeDumpCommand extends AbstractPlayerCommand {

    public BiomeDumpCommand() {
        super("biomedump", "Dump all zone/biome pairs to biomes.txt");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (!(worldGen instanceof ChunkGenerator generator)) {
            context.sendMessage(Message.raw(
                "World generator is not a ChunkGenerator (got "
                    + (worldGen == null ? "null" : worldGen.getClass().getSimpleName()) + ")"));
            return;
        }

        ZonePatternProvider provider = generator.getZonePatternProvider();
        Zone[] zones = provider.getZones();

        StringBuilder sb = new StringBuilder();
        int totalBiomes = 0;
        int totalCustom = 0;

        List<Zone> sorted = new ArrayList<>(List.of(zones));
        sorted.sort((a, b) -> a.name().compareTo(b.name()));

        for (Zone zone : sorted) {
            BiomePatternGenerator bpg = zone.biomePatternGenerator();
            if (bpg == null) {
                sb.append(zone.name()).append("\t<no biome generator>\n");
                continue;
            }

            List<String> biomeNames = new ArrayList<>();
            for (Biome b : bpg.getBiomes()) {
                biomeNames.add(b.getName());
            }
            Collections.sort(biomeNames);
            for (String name : biomeNames) {
                sb.append(zone.name()).append('\t').append(name).append('\n');
            }
            totalBiomes += biomeNames.size();

            CustomBiome[] customs = bpg.getCustomBiomes();
            if (customs != null && customs.length > 0) {
                List<String> customNames = new ArrayList<>();
                for (CustomBiome cb : customs) {
                    customNames.add(cb.getName());
                }
                Collections.sort(customNames);
                for (String name : customNames) {
                    sb.append(zone.name()).append("\t[custom] ").append(name).append('\n');
                }
                totalCustom += customNames.size();
            }
        }

        try {
            Files.writeString(Path.of("biomes.txt"), sb.toString());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error writing biomes.txt: " + e.getMessage()));
            return;
        }

        context.sendMessage(Message.raw(String.format(
            "Found %d zones, %d biomes, %d custom biomes. Written to biomes.txt",
            sorted.size(), totalBiomes, totalCustom)));
    }
}
