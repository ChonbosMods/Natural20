package com.chonbosmods.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;

public class EventTitleTestCommand extends AbstractPlayerCommand {

    public EventTitleTestCommand() {
        super("titletest", "Test EventTitle display with custom text");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        // Test 1: Major banner (the biome-style display)
        EventTitleUtil.showEventTitleToPlayer(playerRef,
                Message.raw("Thornfield"),      // primary (large)
                Message.raw("SETTLEMENT"),       // secondary (small)
                true,                            // isMajor = Major.ui banner
                null,                            // no icon
                5.0f, 1.0f, 1.5f);              // duration, fadeIn, fadeOut

        context.sendMessage(Message.raw("Showing major EventTitle..."));
    }
}
