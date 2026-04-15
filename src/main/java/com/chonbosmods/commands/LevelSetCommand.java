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

/** Dev command: /nat20 levelset <level> — sets totalXp to cumulativeXp(level), recomputes. No banner. */
public class LevelSetCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> levelArg =
            withRequiredArg("level", "Target level (1-40)", ArgTypes.STRING);

    public LevelSetCommand() {
        super("levelset", "Set player level: /nat20 levelset 20");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        int level;
        try { level = Integer.parseInt(levelArg.get(context).trim()); }
        catch (NumberFormatException e) {
            context.sendMessage(Message.raw("Usage: /nat20 levelset <level>"));
            return;
        }
        if (level < 1 || level > Nat20XpMath.LEVEL_CAP) {
            context.sendMessage(Message.raw("Level must be 1-" + Nat20XpMath.LEVEL_CAP + "."));
            return;
        }
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (data == null) {
            context.sendMessage(Message.raw("No player data."));
            return;
        }
        long total = Nat20XpMath.cumulativeXp(level);
        data.setTotalXp(total);
        data.setLevel(level);
        Natural20.getInstance().getPlayerLevelHpSystem().updatePlayerMaxHp(ref, store);
        context.sendMessage(Message.raw("Level set: " + level + " (totalXp=" + total + ")"));
    }
}
