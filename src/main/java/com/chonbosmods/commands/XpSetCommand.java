package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.progression.Nat20XpMath;
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

/** Dev command: /nat20 xpset <amount> — sets totalXp directly, recomputes level. No banner. */
public class XpSetCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> amountArg =
            withRequiredArg("amount", "Total XP", ArgTypes.STRING);

    public XpSetCommand() {
        super("xpset", "Set totalXp directly: /nat20 xpset 50000");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        long amount;
        try { amount = Long.parseLong(amountArg.get(context).trim()); }
        catch (NumberFormatException e) {
            context.sendMessage(Message.raw("Usage: /nat20 xpset <amount>"));
            return;
        }
        if (amount < 0) {
            context.sendMessage(Message.raw("Amount must be non-negative."));
            return;
        }
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (data == null) {
            context.sendMessage(Message.raw("No player data."));
            return;
        }
        data.setTotalXp(amount);
        data.setLevel(Nat20XpMath.levelForTotalXp(amount));
        Natural20.getInstance().getPlayerLevelHpSystem().updatePlayerMaxHp(ref, store);
        context.sendMessage(Message.raw("XP set: totalXp=" + amount + " level=" + data.getLevel()));
    }
}
