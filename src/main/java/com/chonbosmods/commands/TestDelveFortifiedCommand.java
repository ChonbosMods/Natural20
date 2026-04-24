package com.chonbosmods.commands;

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
 * Debug command: gives an Epic Iron Pickaxe with {@code nat20:delve} AND
 * {@code nat20:fortified}. Verifies that shape mining cascade interacts correctly with
 * the Fortified bytecode patch (each cascade block rolls its own skip chance for durability,
 * interpolated from Fortified's Epic ValuesPerRarity range with midLevel = 1.0 → 60%).
 *
 * <p>Epic is used instead of Legendary because Fortified's ValuesPerRarity intentionally
 * tops out at Epic; Legendary gear always rolls Indestructible instead.
 */
public class TestDelveFortifiedCommand extends AbstractPlayerCommand {

    public TestDelveFortifiedCommand() {
        super("testdelvefort", "Give an Epic Iron Pickaxe with Delve + Fortified");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Nat20LootData lootData = new Nat20LootData();
        lootData.setRarity("Epic");
        lootData.setLootLevel(1.0);
        lootData.setAffixes(List.of(
            new RolledAffix("nat20:delve", 1.0),
            new RolledAffix("nat20:fortified", 1.0)
        ));
        lootData.setSockets(0);
        lootData.setGeneratedName("Delve + Fortified Pickaxe");
        lootData.setNamePrefixSource("nat20:delve");

        ItemStack stack = new ItemStack("Tool_Pickaxe_Iron", 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        player.giveItem(stack, ref, store);
        context.sendMessage(Message.raw("Gave: " + lootData.getGeneratedName()
            + " [Epic] (Delve cascade + 60% durability skip per block)"));
    }
}
