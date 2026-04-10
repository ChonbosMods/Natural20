package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.stats.Stat;
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

public class SetStatsCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> pairsArg =
        withRequiredArg("pairs", "Stat pairs: STR 20 DEX 14 ...", ArgTypes.GREEDY_STRING);

    public SetStatsCommand() {
        super("setstats", "Set ability scores: /nat20 setstats STR 20 DEX 14");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (data == null) {
            data = store.addComponent(ref, Natural20.getPlayerDataType());
        }

        String raw = pairsArg.get(context);
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length < 2 || tokens.length % 2 != 0) {
            context.sendMessage(Message.raw("Usage: /nat20 setstats STR 20 DEX 14 ..."));
            return;
        }

        int[] newStats = data.getStats().clone();
        StringBuilder confirmation = new StringBuilder();

        for (int i = 0; i < tokens.length; i += 2) {
            String name = tokens[i].toUpperCase();
            String valueStr = tokens[i + 1];

            Stat stat;
            try {
                stat = Stat.valueOf(name);
            } catch (IllegalArgumentException e) {
                context.sendMessage(Message.raw("Unknown stat: " + tokens[i]
                    + ". Valid: STR, DEX, CON, INT, WIS, CHA"));
                return;
            }

            int value;
            try {
                value = Integer.parseInt(valueStr);
            } catch (NumberFormatException e) {
                context.sendMessage(Message.raw("Invalid number: " + valueStr));
                return;
            }

            if (value < 1 || value > 30) {
                context.sendMessage(Message.raw("Value must be 1-30, got: " + value));
                return;
            }

            newStats[stat.index()] = value;
            int modifier = Math.floorDiv(value - 10, 2);
            String sign = modifier >= 0 ? "+" : "";
            if (confirmation.length() > 0) confirmation.append(", ");
            confirmation.append(stat.name()).append(" ").append(value)
                .append(" (").append(sign).append(modifier).append(")");
        }

        data.setStats(newStats);
        context.sendMessage(Message.raw("Stats set: " + confirmation));
    }
}
