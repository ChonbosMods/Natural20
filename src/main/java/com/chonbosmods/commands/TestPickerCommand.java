package com.chonbosmods.commands;

import com.chonbosmods.ui.BackgroundPickerPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Dev command: {@code /nat20 testpicker} opens {@link BackgroundPickerPage}
 * for iteration without having to wipe first-join state (Task 5.1).
 */
public class TestPickerCommand extends AbstractPlayerCommand {

    public TestPickerCommand() {
        super("testpicker", "Open the background picker: /nat20 testpicker");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("No player."));
            return;
        }
        BackgroundPickerPage page = new BackgroundPickerPage(playerRef);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
