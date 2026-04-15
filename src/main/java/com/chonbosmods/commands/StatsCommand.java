package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.progression.Nat20XpMath;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Dev/player command: /nat20 stats — prints level, XP-into-current-level / XP-to-next, pending ability points.
 * Substitute for the deferred stat-sheet UI: gives players visibility into progression state.
 */
public class StatsCommand extends AbstractPlayerCommand {

    public StatsCommand() {
        super("stats", "Show level + XP + pending ability points");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (data == null) {
            context.sendMessage(Message.raw("No player data."));
            return;
        }
        int level = data.getLevel();
        long total = data.getTotalXp();
        long levelStart = Nat20XpMath.cumulativeXp(level);
        long intoLevel = total - levelStart;
        int toNext = level >= Nat20XpMath.LEVEL_CAP ? 0 : Nat20XpMath.xpToNextLevel(level);
        context.sendMessage(Message.raw(
                "Lv. " + level + " | XP " + intoLevel + (toNext > 0 ? " / " + toNext : " (cap)")
                        + " | Pending pts: " + data.getPendingAbilityPoints()));
    }
}
