package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Dev command: /nat20 xpadd <amount> — awards XP via Nat20XpService (fires banner + HP update on level cross). */
public class XpAddCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> amountArg =
            withRequiredArg("amount", "XP amount", ArgTypes.STRING);

    public XpAddCommand() {
        super("xpadd", "Award XP: /nat20 xpadd 1000");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        int amount;
        try { amount = Integer.parseInt(amountArg.get(context).trim()); }
        catch (NumberFormatException e) {
            context.sendMessage(Message.raw("Usage: /nat20 xpadd <amount>"));
            return;
        }
        if (amount <= 0) {
            context.sendMessage(Message.raw("Amount must be positive."));
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (player == null || data == null) {
            context.sendMessage(Message.raw("No player data."));
            return;
        }
        Natural20.getInstance().getXpService().award(player, ref, store, amount, "command:xpadd");
    }
}
