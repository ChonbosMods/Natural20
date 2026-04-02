package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20ItemDisplayData;
import com.chonbosmods.loot.Nat20ItemRenderer;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.SocketedGem;
import com.chonbosmods.loot.display.AffixLine;
import com.chonbosmods.loot.display.SocketLine;
import com.chonbosmods.stats.PlayerStats;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class LootInspectCommand extends AbstractPlayerCommand {

    public LootInspectCommand() {
        super("lootinspect", "Inspect loot metadata on held item");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        // Get held item from player inventory
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

        Nat20LootData lootData = held.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) {
            context.sendMessage(Message.raw("This item has no Nat20 loot data."));
            return;
        }

        // Print raw metadata fields
        context.sendMessage(Message.raw("--- Nat20 Loot Data ---").color("#ffcc00"));
        context.sendMessage(Message.raw("Name: " + lootData.getGeneratedName()));
        context.sendMessage(Message.raw("Rarity: " + lootData.getRarity()));
        context.sendMessage(Message.raw("Loot Level: " + String.format("%.4f", lootData.getLootLevel())));
        context.sendMessage(Message.raw("Sockets: " + lootData.getSockets()));

        // Name sources
        String prefix = lootData.getNamePrefixSource();
        String suffix = lootData.getNameSuffixSource();
        context.sendMessage(Message.raw("Name Prefix Source: " + (prefix != null ? prefix : "(none)")));
        context.sendMessage(Message.raw("Name Suffix Source: " + (suffix != null ? suffix : "(none)")));

        // Description
        String desc = lootData.getDescription();
        if (desc != null && !desc.isEmpty()) {
            context.sendMessage(Message.raw("Description:"));
            for (String line : desc.split("\n")) {
                context.sendMessage(Message.raw("  " + line));
            }
        } else {
            context.sendMessage(Message.raw("Description: (none)"));
        }

        // Affixes
        if (lootData.getAffixes().isEmpty()) {
            context.sendMessage(Message.raw("Affixes: (none)"));
        } else {
            context.sendMessage(Message.raw("Affixes:"));
            for (RolledAffix affix : lootData.getAffixes()) {
                context.sendMessage(Message.raw("  " + affix.id() + " (level=" + String.format("%.4f", affix.level()) + ")"));
            }
        }

        // Gems
        if (lootData.getGems().isEmpty()) {
            context.sendMessage(Message.raw("Gems: (none)"));
        } else {
            context.sendMessage(Message.raw("Gems:"));
            for (SocketedGem gem : lootData.getGems()) {
                context.sendMessage(Message.raw("  " + gem.id() + " (purity=" + gem.purity().key() + ")"));
            }
        }

        // Resolve and print computed display data using Nat20ItemRenderer
        printResolvedDisplay(context, store, ref, held);
    }

    private void printResolvedDisplay(@Nonnull CommandContext context,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull Ref<EntityStore> ref,
                                      @Nonnull ItemStack held) {
        @Nullable Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        PlayerStats playerStats = playerData != null ? PlayerStats.from(playerData) : null;

        Nat20ItemRenderer renderer = Natural20.getInstance().getLootSystem().getItemRenderer();
        Nat20ItemDisplayData display = renderer.resolve(held, playerStats);

        if (display == null) {
            context.sendMessage(Message.raw("(Renderer returned null: missing rarity definition?)").color("#cc3333"));
            return;
        }

        context.sendMessage(Message.raw("--- Resolved Display ---").color("#ffcc00"));
        context.sendMessage(Message.raw(display.name()).color(display.rarityColor()));
        context.sendMessage(Message.raw(display.rarity()).color(display.rarityColor()));

        for (AffixLine affix : display.affixes()) {
            String line = affix.value() + affix.unit() + " " + affix.statName() + " (" + affix.name() + ")";
            String color = affix.requirementMet() ? "#aaaaaa" : "#cc3333";
            context.sendMessage(Message.raw(line).color(color));
        }
        for (SocketLine socket : display.sockets()) {
            if (socket.filled()) {
                String line = "[" + socket.purity() + " " + socket.gemName() + "]";
                context.sendMessage(Message.raw(line).color(socket.gemColor()));
            } else {
                context.sendMessage(Message.raw("[ Empty Socket ]").color("#666666"));
            }
        }
        if (display.requirement() != null) {
            String color = display.requirement().met() ? "#33cc33" : "#cc3333";
            context.sendMessage(Message.raw("Requires: " + display.requirement().text()).color(color));
        }

        if (playerStats != null) {
            context.sendMessage(Message.raw("(Player level " + playerStats.level() + ", stats applied)").color("#888888"));
        } else {
            context.sendMessage(Message.raw("(No player data: base values shown)").color("#888888"));
        }
    }
}
