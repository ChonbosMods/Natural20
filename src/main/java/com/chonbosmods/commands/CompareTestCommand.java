package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.loot.Nat20ItemRenderer;
import com.chonbosmods.loot.display.ComparisonDeltas;
import com.chonbosmods.loot.display.Nat20TooltipBuilder;
import com.chonbosmods.stats.PlayerStats;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CompareTestCommand extends AbstractPlayerCommand {

    public CompareTestCommand() {
        super("compare", "Compare held item tooltip against first equipped Nat20 item");
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

        ItemStack held = InventoryComponent.getItemInHand(store, ref);
        if (held == null || held.isEmpty()) {
            context.sendMessage(Message.raw("No item in hand."));
            return;
        }

        @Nullable Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        PlayerStats playerStats = playerData != null ? PlayerStats.from(playerData) : null;

        Nat20ItemRenderer renderer = Natural20.getInstance().getLootSystem().getItemRenderer();
        Nat20ItemDisplayData hoveredDisplay = renderer.resolve(held, playerStats);
        if (hoveredDisplay == null) {
            context.sendMessage(Message.raw("Held item is not a Nat20 item."));
            return;
        }

        // Find first equipped Nat20 item to compare against
        Nat20ItemDisplayData equippedDisplay = findFirstEquipped(store, ref, renderer, playerStats);
        if (equippedDisplay == null) {
            context.sendMessage(Message.raw("No equipped Nat20 item found for comparison. Showing tooltip without deltas:"));
            playerRef.sendMessage(Nat20TooltipBuilder.build(hoveredDisplay, null));
            return;
        }

        ComparisonDeltas deltas = ComparisonDeltas.compute(hoveredDisplay, equippedDisplay);
        context.sendMessage(Message.raw("--- Comparing against: " + equippedDisplay.name() + " ---").color("#ffcc00"));
        playerRef.sendMessage(Nat20TooltipBuilder.build(hoveredDisplay, deltas));
    }

    @Nullable
    private Nat20ItemDisplayData findFirstEquipped(Store<EntityStore> store, Ref<EntityStore> ref,
                                                    Nat20ItemRenderer renderer,
                                                    @Nullable PlayerStats playerStats) {
        CombinedItemContainer combined = InventoryComponent.getCombined(
                store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        if (combined == null) return null;
        for (short i = 0; i < combined.getCapacity(); i++) {
            ItemStack stack = combined.getItemStack(i);
            if (stack == null || stack.isEmpty()) continue;
            Nat20ItemDisplayData display = renderer.resolve(stack, playerStats);
            if (display != null) return display;
        }
        return null;
    }
}
