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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import javax.annotation.Nonnull;
import java.util.Random;

public class LootCommand extends AbstractPlayerCommand {

    private final RequiredArg<Item> itemArg =
        withRequiredArg("item", "Hytale item (e.g., IronSword)", ArgTypes.ITEM_ASSET);

    public LootCommand() {
        super("loot", "Generate a loot item and add to inventory");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Item item = itemArg.get(context);
        String itemId = item.getId();
        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();

        // Resolve equipment category
        EquipmentCategory category = lootSystem.getLootEntryRegistry().getManualCategory(itemId);
        String categoryKey;
        if (category != null) {
            categoryKey = category.key();
        } else {
            categoryKey = autoDetectCategory(item);
            if (categoryKey == null) {
                context.sendMessage(Message.raw("Cannot determine equipment category for: " + itemId));
                return;
            }
        }

        // Generate loot
        Random random = new Random();
        String baseName = deriveBaseName(itemId);

        Nat20LootData lootData = lootSystem.getPipeline().generate(itemId, baseName, categoryKey, random);
        if (lootData == null) {
            context.sendMessage(Message.raw("Failed to generate loot for: " + itemId));
            return;
        }

        // Create ItemStack with loot metadata (use canonical item.getId() for correct resolution)
        ItemStack stack = new ItemStack(itemId, 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        // Add to player inventory via proper giveItem API
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        player.giveItem(stack, ref, store);

        context.sendMessage(Message.raw("Generated: " + lootData.getGeneratedName() +
            " [" + lootData.getRarity() + "] (" + lootData.getAffixes().size() + " affixes, " +
            lootData.getSockets() + " sockets, lootLevel=" +
            String.format("%.2f", lootData.getLootLevel()) + ")"));

        for (var affix : lootData.getAffixes()) {
            context.sendMessage(Message.raw("  Affix: " + affix.id() + " (level=" + String.format("%.2f", affix.level()) + ")"));
        }
    }

    /**
     * Derive a human-readable base name from a canonical item ID.
     * "Weapon_Sword_Iron" → "Iron Sword", "Armor_Iron_Chest" → "Iron Chest"
     */
    private String deriveBaseName(String itemId) {
        // Strip type prefix (Weapon_, Tool_, Armor_)
        String name = itemId;
        for (String prefix : new String[]{"Weapon_", "Tool_", "Armor_"}) {
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
                break;
            }
        }
        // Replace underscores with spaces
        name = name.replace('_', ' ');
        // Reverse word order so material comes first: "Sword Iron" → "Iron Sword"
        String[] words = name.split(" ");
        if (words.length >= 2) {
            String last = words[words.length - 1];
            StringBuilder sb = new StringBuilder(last);
            for (int i = 0; i < words.length - 1; i++) {
                sb.append(' ').append(words[i]);
            }
            return sb.toString();
        }
        return name;
    }

    private String autoDetectCategory(Item item) {
        try {
            if (item.getWeapon() != null) return "melee_weapon";
            if (item.getArmor() != null) return "armor";
            if (item.getTool() != null) return "tool";
            if (item.getUtility() != null) return "utility";
        } catch (Exception e) {
            // Property access failed
        }
        return null;
    }
}
