package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.world.Nat20ZoneRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ZoneDumpCommand extends AbstractPlayerCommand {

    public ZoneDumpCommand() {
        super("zonedump", "Dump all Hytale zone ids to zones.txt");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Nat20ZoneRegistry registry = Natural20.getInstance().getZoneRegistry();
        if (!registry.isInitialized()) {
            registry.initialize(world);
        }

        List<String> zones = registry.getZoneNames();
        StringBuilder sb = new StringBuilder();
        for (String z : zones) {
            sb.append(z).append("\n");
        }

        try {
            Files.writeString(Path.of("zones.txt"), sb.toString());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error writing zones.txt: " + e.getMessage()));
            return;
        }

        context.sendMessage(Message.raw(
            "Found " + zones.size() + " zones. Written to zones.txt"));
    }
}
