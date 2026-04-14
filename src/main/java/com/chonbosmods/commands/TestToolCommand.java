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
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
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

/**
 * Debug command: spawns an Iron Pickaxe with a single tool affix for testing.
 *
 * <p>Usage: {@code /nat20 testtool <affix> [debug]}
 * <ul>
 *   <li>{@code affix}: affix id (e.g., {@code haste}, {@code indestructible}, {@code fortified},
 *       {@code quake}). Auto-prefixes {@code nat20:} if omitted.</li>
 *   <li>{@code debug}: optional numeric override. For {@code haste}, this value is the raw additive
 *       bonus and bypasses softcap/scaling via the {@code debug:haste:} sentinel in
 *       {@code Nat20HasteSystem} (e.g., {@code 2.0} → 3x mining speed). Ignored for other affixes.</li>
 * </ul>
 *
 * <p>Always rolls at Legendary so affixes gated to higher rarities (e.g., indestructible) work.
 */
public class TestToolCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> affixArg =
        withRequiredArg("affix", "Tool affix ID: haste, indestructible, fortified, quake, etc.", ArgTypes.STRING);
    private final OptionalArg<String> debugArg =
        withOptionalArg("debug", "Haste-only: raw bonus override bypassing softcap (e.g., 2.0 = 3x speed)", ArgTypes.STRING);

    public TestToolCommand() {
        super("testtool", "Give an Iron Pickaxe with a tool affix: /nat20 testtool haste [2.0]");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String input = affixArg.get(context).trim();
        String affixId = input.contains(":") ? input : "nat20:" + input;

        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();
        Nat20AffixDef affixDef = lootSystem.getAffixRegistry().get(affixId);
        if (affixDef == null) {
            context.sendMessage(Message.raw("Unknown affix: " + input
                + ". Check loot/affixes/ JSON files for valid IDs."));
            return;
        }

        Nat20LootData lootData = new Nat20LootData();
        lootData.setRarity("Legendary");
        lootData.setLootLevel(1.0);
        lootData.setAffixes(List.of(new RolledAffix(affixId, 1.0)));
        lootData.setSockets(0);
        lootData.setNamePrefixSource(affixId);

        String label = capitalize(input);
        lootData.setGeneratedName("Test " + label + " Pickaxe");

        // Haste-only debug sentinel: bakes a raw multiplier into uniqueItemId which
        // Nat20HasteSystem honors by skipping softcap/scaling. Used for obvious 3x speed tests.
        if ("nat20:haste".equals(affixId) && context.provided(debugArg)) {
            double bonus;
            try {
                bonus = Double.parseDouble(debugArg.get(context).trim());
            } catch (NumberFormatException e) {
                context.sendMessage(Message.raw("Invalid debug bonus; use a number like 2.0 for 3x speed."));
                return;
            }
            lootData.setUniqueItemId("debug:haste:" + bonus);
            lootData.setGeneratedName(String.format("Debug Haste Pickaxe (+%.1f)", bonus));
        }

        ItemStack stack = new ItemStack("Tool_Pickaxe_Iron", 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access player entity."));
            return;
        }

        player.giveItem(stack, ref, store);
        context.sendMessage(Message.raw("Gave: " + lootData.getGeneratedName()
            + " [Legendary] with affix " + affixId));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
