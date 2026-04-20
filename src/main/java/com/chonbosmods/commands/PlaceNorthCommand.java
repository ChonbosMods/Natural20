package com.chonbosmods.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** {@code /nat20 placenorth} - entrance faces +Z after rotation. */
public class PlaceNorthCommand extends AbstractPlayerCommand {
    public PlaceNorthCommand() {
        super("placenorth", "Paste testStructure with entrance rotated to face +Z");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        PlaceAllCommand.placeAndSpawn(ctx, store, ref, world, new Vector3i(0, 0, 1));
    }
}
