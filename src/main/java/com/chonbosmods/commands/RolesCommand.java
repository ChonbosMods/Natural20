package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.npc.NPCPlugin;

import javax.annotation.Nonnull;

public class RolesCommand extends CommandBase {

    public RolesCommand() {
        super("roles", "List registered NPC role names (debug)");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        try {
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null) {
                context.sendMessage(Message.raw("NPCPlugin not loaded."));
                return;
            }

            StringBuilder all = new StringBuilder();
            StringBuilder humanMatches = new StringBuilder();
            int count = 0;

            // Iterate role indices until getName returns null
            for (int i = 0; i < 10000; i++) {
                String name = npcPlugin.getName(i);
                if (name == null) break;
                all.append(i).append("\t").append(name).append("\n");
                count++;

                String lower = name.toLowerCase();
                if (lower.contains("human") || lower.contains("villager") ||
                    lower.contains("npc") || lower.contains("citizen") ||
                    lower.contains("guard") || lower.contains("merchant") ||
                    lower.contains("trader") || lower.contains("shop")) {
                    humanMatches.append(i).append("\t").append(name).append("\n");
                }
            }

            try {
                java.nio.file.Files.writeString(
                    java.nio.file.Path.of("npc_roles.txt"), all.toString());
                java.nio.file.Files.writeString(
                    java.nio.file.Path.of("npc_roles_matches.txt"), humanMatches.toString());
            } catch (Exception e) {
                context.sendMessage(Message.raw("Error writing files: " + e.getMessage()));
            }

            context.sendMessage(Message.raw("Found " + count + " roles. Written to npc_roles.txt. Matches:\n" + humanMatches));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error accessing NPCPlugin: " + e.getMessage()));
        }
    }
}
