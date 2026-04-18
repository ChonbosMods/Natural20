package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.progression.Nat20HostilePool;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HostileDumpCommand extends CommandBase {

    public HostileDumpCommand() {
        super("hostiledump", "Dump the enumerated hostile mob pool to hostile_roles.txt");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Nat20HostilePool pool = Natural20.getInstance().getHostilePool();
        if (!pool.isInitialized()) {
            context.sendMessage(Message.raw("HostilePool not initialized yet."));
            return;
        }

        List<String> roles = pool.getHostileRoles();
        StringBuilder sb = new StringBuilder();
        for (String role : roles) {
            sb.append(role).append("\n");
        }

        try {
            Files.writeString(Path.of("hostile_roles.txt"), sb.toString());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error writing file: " + e.getMessage()));
            return;
        }

        context.sendMessage(Message.raw(
            "Found " + roles.size() + " hostile roles. Written to hostile_roles.txt"));
    }
}
