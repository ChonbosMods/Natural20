package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;

public class TestWeaponCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> affixArg =
        withRequiredArg("affix", "Affix ID: mighty, swift, vampiric, etc.", ArgTypes.STRING);

    public TestWeaponCommand() {
        super("testweapon", "Give an Iron Sword with a specific affix: /nat20 testweapon mighty");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String input = affixArg.get(context).trim();
        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();

        // Resolve affix ID (auto-prefix nat20: if needed)
        String affixId = input.contains(":") ? input : "nat20:" + input;
        Nat20AffixDef affixDef = lootSystem.getAffixRegistry().get(affixId);
        if (affixDef == null) {
            context.sendMessage(Message.raw("Unknown affix: " + input
                + ". Check loot/affixes/ JSON files for valid IDs."));
            return;
        }

        // Generate through the pipeline to get proper unique ID, tooltip, and item registration
        Nat20RarityDef rarityDef = lootSystem.getRarityRegistry().get("Rare");
        if (rarityDef == null) {
            context.sendMessage(Message.raw("Rare rarity not loaded."));
            return;
        }
        int tier = rarityDef.qualityValue();
        Random random = new Random();
        Nat20LootData lootData = lootSystem.getPipeline().generate(
            "Weapon_Sword_Iron", "Iron Sword", "melee_weapon", tier, tier, random);
        if (lootData == null) {
            context.sendMessage(Message.raw("Failed to generate base weapon."));
            return;
        }

        // Override the affixes with exactly our desired affix
        lootData.setAffixes(List.of(new RolledAffix(affixId, 0.8)));
        lootData.setNamePrefixSource(affixId);

        // Re-register with item registry so tooltip reflects the forced affix
        String uniqueId = lootData.getUniqueItemId();
        if (uniqueId == null) {
            uniqueId = "Weapon_Sword_Iron";
        }
        // Update tooltip by re-registering (unregister old, register new)
        if (lootData.getUniqueItemId() != null) {
            lootSystem.getItemRegistry().unregisterItem(lootData.getUniqueItemId());
            String newUniqueId = lootSystem.getItemRegistry().registerItem(
                "Weapon_Sword_Iron", "Rare", lootData);
            if (newUniqueId != null) {
                lootData.setUniqueItemId(newUniqueId);
                uniqueId = newUniqueId;
            }
        }

        // Create ItemStack with the unique item ID and metadata
        ItemStack stack = new ItemStack(uniqueId, 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        player.giveItem(stack, ref, store);
        context.sendMessage(Message.raw("Gave: " + lootData.getGeneratedName()
            + " [Rare] with forced affix " + affixId));
    }
}
