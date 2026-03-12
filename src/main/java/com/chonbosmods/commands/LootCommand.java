package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.EquipmentCategory;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import javax.annotation.Nonnull;
import java.util.Random;

public class LootCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> itemIdArg =
        withRequiredArg("item", "Hytale item ID (e.g., Hytale:IronSword)", ArgTypes.STRING);

    public LootCommand() {
        super("loot", "Generate a loot item and add to inventory");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String itemId = itemIdArg.get(context);
        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();

        // Resolve equipment category
        EquipmentCategory category = lootSystem.getLootEntryRegistry().getManualCategory(itemId);
        String categoryKey;
        if (category != null) {
            categoryKey = category.key();
        } else {
            categoryKey = autoDetectCategory(itemId);
            if (categoryKey == null) {
                context.sendMessage(Message.raw("Cannot determine equipment category for: " + itemId));
                return;
            }
        }

        // Generate loot
        Random random = new Random();
        String baseName = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        baseName = baseName.replaceAll("([a-z])([A-Z])", "$1 $2");

        Nat20LootData lootData = lootSystem.getPipeline().generate(itemId, baseName, categoryKey, random);
        if (lootData == null) {
            context.sendMessage(Message.raw("Failed to generate loot for: " + itemId));
            return;
        }

        // Create ItemStack with loot metadata
        ItemStack stack = new ItemStack(itemId, 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        // Add to player inventory
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        Inventory inv = player.getInventory();
        inv.getHotbar().addItemStack(stack);

        context.sendMessage(Message.raw("Generated: " + lootData.getGeneratedName() +
            " [" + lootData.getRarity() + "] (" + lootData.getAffixes().size() + " affixes, " +
            lootData.getSockets() + " sockets, lootLevel=" +
            String.format("%.2f", lootData.getLootLevel()) + ")"));

        for (var affix : lootData.getAffixes()) {
            context.sendMessage(Message.raw("  Affix: " + affix.id() + " (level=" + String.format("%.2f", affix.level()) + ")"));
        }
    }

    private String autoDetectCategory(String itemId) {
        try {
            var item = Item.getAssetMap().getAsset(itemId);
            if (item == null) return null;
            if (item.getWeapon() != null) return "melee_weapon";
            if (item.getArmor() != null) return "armor";
            if (item.getTool() != null) return "tool";
            if (item.getUtility() != null) return "utility";
        } catch (Exception e) {
            // Asset not found
        }
        return null;
    }
}
