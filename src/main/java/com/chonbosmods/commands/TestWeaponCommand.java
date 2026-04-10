package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.Nat20AffixDef;
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

        // Build loot data manually with exactly this one affix at max roll
        Nat20LootData lootData = new Nat20LootData();
        lootData.setRarity("Rare");
        lootData.setLootLevel(0.8);
        lootData.setAffixes(List.of(new RolledAffix(affixId, 0.8)));
        lootData.setSockets(0);

        // Name the weapon after the affix
        String displayName = affixDef.displayName();
        String cleanName = input.contains(":") ? input.substring(input.indexOf(':') + 1) : input;
        String weaponName = capitalize(cleanName) + " Iron Sword";
        lootData.setGeneratedName(weaponName);
        lootData.setNamePrefixSource(affixId);

        // Create ItemStack with metadata
        ItemStack stack = new ItemStack("Weapon_Sword_Iron", 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        player.giveItem(stack, ref, store);
        context.sendMessage(Message.raw("Gave: " + weaponName + " [Rare] with affix " + affixId));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
