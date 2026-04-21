package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Debug command: dumps every registered BlockType whose asset key mentions "chest"
 * (case-insensitive). Used to discover the real block-type ids that must live in
 * {@code config/chest_loot.json} so {@code Nat20ChestAffixInjectionSystem} actually
 * matches native chests in this server build.
 */
public class DumpChestsCommand extends CommandBase {

    public DumpChestsCommand() {
        super("dumpchests", "List block types whose name contains 'chest' (debug)");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<String> matches = new ArrayList<>();
        for (String key : BlockType.getAssetMap().getAssetMap().keySet()) {
            if (key.toLowerCase().contains("chest")) {
                matches.add(key);
            }
        }
        Collections.sort(matches);

        try {
            Files.writeString(Path.of("chest_block_types.txt"), String.join("\n", matches));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error writing chest_block_types.txt: " + e.getMessage()));
        }

        StringBuilder msg = new StringBuilder("Found ").append(matches.size()).append(" chest block types:\n");
        for (String m : matches) {
            msg.append(m).append("\n");
        }
        context.sendMessage(Message.raw(msg.toString()));
    }
}
