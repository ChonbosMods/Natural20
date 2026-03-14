package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.loot.Nat20ItemRenderer;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.display.Nat20TooltipBuilder;
import com.chonbosmods.stats.PlayerStats;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Custom equipment inspection page that displays the player's armor and hotbar
 * items with rich Nat20 tooltips (affixes, sockets, rarity, etc.).
 *
 * Uses individual Group wrappers with TooltipTextSpans so hovering each slot
 * shows the full Nat20 tooltip via the client's built-in text tooltip system.
 */
public class Nat20EquipmentPage extends CustomUIPage {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|EquipPage");

    private static final String PAGE_LAYOUT = "Pages/Nat20_Equipment.ui";

    private static final int ARMOR_SLOTS = 4;
    private static final int HOTBAR_SLOTS = 9;

    public Nat20EquipmentPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append(PAGE_LAYOUT);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            LOGGER.atWarning().log("Could not resolve player for equipment page");
            return;
        }

        Inventory inventory = player.getInventory();

        // Resolve player stats for affix scaling
        @Nullable Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        @Nullable PlayerStats playerStats = playerData != null ? PlayerStats.from(playerData) : null;

        Nat20ItemRenderer renderer = Natural20.getInstance().getLootSystem().getItemRenderer();

        // Populate armor slots
        ItemContainer armor = inventory.getArmor();
        for (short slot = 0; slot < ARMOR_SLOTS && slot < armor.getCapacity(); slot++) {
            String slotSelector = "#EqArmorSlot" + slot;
            String iconSelector = "#EqArmorIcon" + slot;
            populateSlot(cmd, renderer, playerStats, armor, slot, slotSelector, iconSelector);
        }

        // Populate hotbar slots
        ItemContainer hotbar = inventory.getHotbar();
        for (short slot = 0; slot < HOTBAR_SLOTS && slot < hotbar.getCapacity(); slot++) {
            String slotSelector = "#EqHotbarSlot" + slot;
            String iconSelector = "#EqHotbarIcon" + slot;
            populateSlot(cmd, renderer, playerStats, hotbar, slot, slotSelector, iconSelector);
        }
    }

    /**
     * Populate a single equipment slot: set the item icon and tooltip.
     */
    private void populateSlot(UICommandBuilder cmd, Nat20ItemRenderer renderer,
                              @Nullable PlayerStats playerStats,
                              ItemContainer container, short slot,
                              String slotSelector, String iconSelector) {
        ItemStack stack = container.getItemStack(slot);
        if (stack == null || stack.isEmpty()) {
            // Hide only the icon, keep the slot background visible for grid layout
            cmd.set(iconSelector + ".Visible", false);
            return;
        }

        // Set the item icon
        cmd.set(iconSelector + ".ItemId", stack.getItemId());

        // Build tooltip if item has Nat20 loot data
        Nat20LootData lootData = stack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) {
            // Non-Nat20 item: show basic item ID as tooltip
            cmd.set(slotSelector + ".TooltipTextSpans",
                    Message.raw(stack.getItemId()).color("#FFFFFF"));
            return;
        }

        Nat20ItemDisplayData displayData = renderer.resolve(stack, playerStats);
        if (displayData == null) {
            cmd.set(slotSelector + ".TooltipTextSpans",
                    Message.raw(stack.getItemId()).color("#FFFFFF"));
            return;
        }

        // Build the rich tooltip (no comparison deltas in this first iteration)
        Message tooltip = Nat20TooltipBuilder.build(displayData, null);
        cmd.set(slotSelector + ".TooltipTextSpans", tooltip);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, String rawData) {
        // No interactive events in this page
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
    }
}
