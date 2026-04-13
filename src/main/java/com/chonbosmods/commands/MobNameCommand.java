package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.mob.EncounterTier;
import com.chonbosmods.loot.mob.naming.MobNameRarity;
import com.chonbosmods.loot.mob.naming.Nat20MobNameGenerator;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * QA command: generates random elite mob names at a specified tier.
 * Usage: /nat20 mobname ELITE 20
 */
public class MobNameCommand extends CommandBase {

    private final RequiredArg<String> argsArg =
        withRequiredArg("args", "Tier name and optional count: ELITE 20", ArgTypes.GREEDY_STRING);

    public MobNameCommand() {
        super("mobname", "Generate random elite mob names: /nat20 mobname ELITE 20");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        String raw = argsArg.get(context).trim();
        if (raw.isEmpty()) {
            context.sendMessage(Message.raw("Usage: /nat20 mobname <tier> [count]  (tiers: ENHANCED, ELITE, CHAMPION, BOSS)"));
            return;
        }
        String[] tokens = raw.split("\\s+");

        // Parse tier (required)
        String tierStr = tokens[0].toUpperCase();
        EncounterTier tier = EncounterTier.fromName(tierStr);
        if (tier == EncounterTier.NORMAL && !tierStr.equals("NORMAL")) {
            context.sendMessage(Message.raw("Unknown tier: " + tokens[0]
                + ". Valid: ENHANCED, ELITE, CHAMPION, BOSS"));
            return;
        }
        if (tier == EncounterTier.NORMAL) {
            context.sendMessage(Message.raw("NORMAL tier mobs don't get names. Use ENHANCED, ELITE, CHAMPION, or BOSS."));
            return;
        }

        // Parse count (optional, default 10, max 50)
        int count = 10;
        if (tokens.length >= 2) {
            try {
                count = Integer.parseInt(tokens[1]);
            } catch (NumberFormatException e) {
                context.sendMessage(Message.raw("Invalid count: " + tokens[1] + ". Must be a number."));
                return;
            }
            if (count < 1) count = 1;
            if (count > 50) count = 50;
        }

        Nat20MobNameGenerator generator = Natural20.getInstance().getLootSystem().getMobNameGenerator();
        MobNameRarity rarity = MobNameRarity.fromTierOrdinal(tier.ordinal());

        StringBuilder sb = new StringBuilder();
        sb.append("=== Elite Mob Names (tier=").append(tier.name())
            .append(", rarity=").append(rarity.name()).append(") ===\n");

        for (int i = 1; i <= count; i++) {
            String name = generator.generate(tier);
            sb.append(i).append(". ").append(name != null ? name : "(null)").append("\n");
        }

        sb.append("Pool: ").append(generator.getPrefixCount()).append(" prefixes, ")
            .append(generator.getSuffixCount()).append(" suffixes, ")
            .append(generator.getAppellationCount()).append(" appellations");

        context.sendMessage(Message.raw(sb.toString()));
    }
}
