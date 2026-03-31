package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.TreeSet;

/**
 * Debug command: lists available Item asset keys.
 * Use to discover valid item IDs for quest pools.
 */
public class ItemNamesCommand extends CommandBase {

    public ItemNamesCommand() {
        super("itemnames", "List available item type names (debug)");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Set<String> keys = new TreeSet<>(Item.getAssetMap().getAssetMap().keySet());

        // Filter for resource-like items
        StringBuilder matches = new StringBuilder();
        StringBuilder all = new StringBuilder();
        for (String key : keys) {
            all.append(key).append("\n");
            String lower = key.toLowerCase();
            if (lower.contains("meat") || lower.contains("leather") || lower.contains("bone")
                || lower.contains("feather") || lower.contains("wood") || lower.contains("log")
                || lower.contains("stone") || lower.contains("iron") || lower.contains("ore")
                || lower.contains("cotton") || lower.contains("wheat") || lower.contains("clay")
                || lower.contains("flint") || lower.contains("herb") || lower.contains("copper")
                || lower.contains("charcoal") || lower.contains("fiber") || lower.contains("coal")) {
                matches.append(key).append("\n");
            }
        }

        try {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("item_types.txt"), all.toString());
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("item_types_matches.txt"), matches.toString());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error writing files: " + e.getMessage()));
        }

        context.sendMessage(Message.raw("Found " + keys.size() + " item types. Resource matches:\n" + matches));
    }
}
