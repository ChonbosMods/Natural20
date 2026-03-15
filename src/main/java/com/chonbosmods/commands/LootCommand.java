package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.EquipmentCategory;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.chonbosmods.loot.def.Nat20RarityDef;
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
    private final OptionalArg<String> rarityArg =
        withOptionalArg("rarity", "Rarity tier (Common, Uncommon, Rare, Epic, Legendary)", ArgTypes.STRING);

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
        String baseName = lootSystem.getLootEntryRegistry().getDisplayName(itemId);
        if (baseName == null) {
            baseName = lootSystem.getItemRegistry().resolveItemDisplayName(itemId);
        }
        if (baseName == null) {
            baseName = itemId;
        }

        Nat20LootData lootData;
        if (context.provided(rarityArg)) {
            String rarityName = rarityArg.get(context);
            Nat20RarityDef rarity = lootSystem.getRarityRegistry().get(rarityName);
            if (rarity == null) {
                context.sendMessage(Message.raw("Unknown rarity: " + rarityName
                    + ". Valid: Common, Uncommon, Rare, Epic, Legendary"));
                return;
            }
            int tier = rarity.qualityValue();
            lootData = lootSystem.getPipeline().generate(itemId, baseName, categoryKey, tier, tier, random);
        } else {
            lootData = lootSystem.getPipeline().generate(itemId, baseName, categoryKey, random);
        }
        if (lootData == null) {
            context.sendMessage(Message.raw("Failed to generate loot for: " + itemId));
            return;
        }

        // Create ItemStack with unique item ID for per-instance tooltip
        String stackItemId = lootData.getUniqueItemId() != null ? lootData.getUniqueItemId() : itemId;
        ItemStack stack = new ItemStack(stackItemId, 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

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
