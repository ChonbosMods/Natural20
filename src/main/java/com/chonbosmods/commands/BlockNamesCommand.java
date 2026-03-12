package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Debug command: lists available BlockType asset keys.
 * Use to discover valid block names for prefab files.
 */
public class BlockNamesCommand extends CommandBase {

    public BlockNamesCommand() {
        super("blocknames", "List available block type names (debug)");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Set<String> keys = BlockType.getAssetMap().getAssetMap().keySet();

        // Send matches to chat and write all to file
        StringBuilder matches = new StringBuilder();
        StringBuilder all = new StringBuilder();
        for (String key : keys) {
            all.append(key).append("\n");
            String lower = key.toLowerCase();
            if (lower.contains("wood") || lower.contains("plank") || lower.contains("log")
                || lower.contains("cobble") || lower.contains("stone") || lower.contains("rock")) {
                matches.append(key).append("\n");
            }
        }

        // Write full list to file for inspection
        try {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("block_types.txt"), all.toString());
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("block_types_matches.txt"), matches.toString());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error writing files: " + e.getMessage()));
        }

        context.sendMessage(Message.raw("Found " + keys.size() + " block types. Matches:\n" + matches));
    }
}
