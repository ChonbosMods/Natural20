package com.chonbosmods.commands;

import com.chonbosmods.dice.Nat20DiceRoller;
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

/** DEBUG ONLY: forces the kept die on the next d20 skill check to a chosen value
 *  so nat20/nat1 quest-accept consequences can be smoke tested without grinding
 *  the natural 5% probability. Remove once the feature is confirmed working. */
public class ForceRollCommand extends AbstractPlayerCommand {

    private final RequiredArg<Integer> rollArg =
        withRequiredArg("roll", "Forced kept die value 1-20", ArgTypes.INTEGER);

    public ForceRollCommand() {
        super("forceroll", "Force the next d20 kept die to a chosen value (debug)");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        int value = rollArg.get(context);
        if (value < 1 || value > 20) {
            context.sendMessage(Message.raw("Roll must be 1-20, got: " + value));
            return;
        }
        Nat20DiceRoller.setForcedNextRoll(value);
        context.sendMessage(Message.raw("Next d20 kept die forced to " + value
            + ". One-shot; cleared after the next roll."));
    }
}
