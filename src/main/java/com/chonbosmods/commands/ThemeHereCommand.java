package com.chonbosmods.commands;

import com.chonbosmods.world.Nat20BiomeLookup;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ThemeHereCommand extends AbstractPlayerCommand {

    public ThemeHereCommand() {
        super("themehere", "Print the biome id at the player's current position");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        String zone = Nat20BiomeLookup.getZoneName(world, pos.getX(), pos.getZ());
        String biome = Nat20BiomeLookup.getBiomeName(world, pos.getX(), pos.getZ());
        context.sendMessage(Message.raw(String.format(
            "zone=%s  biome=%s  at (%.1f, %.1f, %.1f)",
            zone == null ? "<null>" : zone,
            biome == null ? "<null>" : biome,
            pos.getX(), pos.getY(), pos.getZ())));
    }
}
