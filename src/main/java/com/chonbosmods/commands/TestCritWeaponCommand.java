package com.chonbosmods.commands;

import com.chonbosmods.combat.Nat20CritSystem;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.RolledAffix;
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
import java.util.List;

/**
 * Dev command: gives an Iron Sword with the {@code nat20:force_crit} marker affix.
 * Nat20CritSystem short-circuits the RNG roll when this affix is present, so every
 * hit with this weapon crits. Used to verify the crit visual/sound pipeline.
 */
public class TestCritWeaponCommand extends AbstractPlayerCommand {

    public TestCritWeaponCommand() {
        super("testcritweapon", "Give an Iron Sword that crits on every hit (dev test)");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Nat20LootData lootData = new Nat20LootData();
        lootData.setRarity("Rare");
        lootData.setLootLevel(1.0);
        lootData.setAffixes(List.of(new RolledAffix(Nat20CritSystem.FORCE_CRIT_ID, 1.0)));
        lootData.setSockets(0);
        lootData.setGeneratedName("Guaranteed Crit Sword");
        lootData.setNamePrefixSource(Nat20CritSystem.FORCE_CRIT_ID);

        ItemStack stack = new ItemStack("Weapon_Sword_Iron", 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        player.giveItem(stack, ref, store);
        context.sendMessage(Message.raw("Gave: Guaranteed Crit Sword. Every hit will crit."));
    }
}
