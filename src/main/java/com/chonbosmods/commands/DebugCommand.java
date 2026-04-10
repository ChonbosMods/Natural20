package com.chonbosmods.commands;

import com.chonbosmods.combat.CombatDebugSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

public class DebugCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> modeArg =
        withRequiredArg("mode", "on or off", ArgTypes.STRING);

    public DebugCommand() {
        super("debug", "Toggle combat debug: /nat20 debug on|off");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String mode = modeArg.get(context).toLowerCase();
        UUID uuid = playerRef.getUuid();

        switch (mode) {
            case "on" -> {
                CombatDebugSystem.enable(uuid);
                context.sendMessage(Message.raw("Combat debug enabled."));
            }
            case "off" -> {
                CombatDebugSystem.disable(uuid);
                context.sendMessage(Message.raw("Combat debug disabled."));
            }
            default -> context.sendMessage(Message.raw("Usage: /nat20 debug on|off"));
        }
    }
}
