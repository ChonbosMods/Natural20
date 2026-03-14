package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.loot.Nat20ItemRenderer;
import com.chonbosmods.loot.display.Nat20TooltipBuilder;
import com.chonbosmods.stats.PlayerStats;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TooltipTestCommand extends AbstractPlayerCommand {

    public TooltipTestCommand() {
        super("tooltip", "Show rich tooltip for held item");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        ItemStack held = player.getInventory().getActiveHotbarItem();
        if (held == null || held.isEmpty()) {
            context.sendMessage(Message.raw("No item in hand."));
            return;
        }

        @Nullable Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        PlayerStats playerStats = playerData != null ? PlayerStats.from(playerData) : null;

        Nat20ItemRenderer renderer = Natural20.getInstance().getLootSystem().getItemRenderer();
        Nat20ItemDisplayData display = renderer.resolve(held, playerStats);

        if (display == null) {
            context.sendMessage(Message.raw("Not a Nat20 item."));
            return;
        }

        Message tooltip = Nat20TooltipBuilder.build(display, null);
        playerRef.sendMessage(tooltip);
    }
}
