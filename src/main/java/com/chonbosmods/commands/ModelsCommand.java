package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.Set;

public class ModelsCommand extends CommandBase {

    public ModelsCommand() {
        super("models", "List available model asset names (debug)");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Set<String> keys = ModelAsset.getAssetMap().getAssetMap().keySet();

        StringBuilder all = new StringBuilder();
        StringBuilder matches = new StringBuilder();
        for (String key : keys) {
            all.append(key).append("\n");
            String lower = key.toLowerCase();
            if (lower.contains("human") || lower.contains("character") ||
                lower.contains("villager") || lower.contains("npc") ||
                lower.contains("person") || lower.contains("citizen") ||
                lower.contains("guard") || lower.contains("merchant")) {
                matches.append(key).append("\n");
            }
        }

        try {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("model_assets.txt"), all.toString());
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("model_assets_matches.txt"), matches.toString());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error writing files: " + e.getMessage()));
        }

        context.sendMessage(Message.raw("Found " + keys.size() + " models. Written to model_assets.txt. Matches:\n" + matches));
    }
}
