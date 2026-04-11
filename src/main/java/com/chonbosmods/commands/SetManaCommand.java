package com.chonbosmods.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Dev command: sets the player's max mana pool via a StaticModifier.
 * Usage: /nat20 setmana 100
 */
public class SetManaCommand extends AbstractPlayerCommand {

    private static final String MODIFIER_KEY = "nat20:dev_max_mana";

    private final RequiredArg<String> amountArg =
        withRequiredArg("amount", "Max mana pool size", ArgTypes.STRING);

    public SetManaCommand() {
        super("setmana", "Set max mana pool: /nat20 setmana 100");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        int amount;
        try {
            amount = Integer.parseInt(amountArg.get(context).trim());
        } catch (NumberFormatException e) {
            context.sendMessage(Message.raw("Usage: /nat20 setmana 100"));
            return;
        }
        if (amount < 0 || amount > 10000) {
            context.sendMessage(Message.raw("Value must be 0-10000."));
            return;
        }

        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            context.sendMessage(Message.raw("No stat map on player entity."));
            return;
        }

        int manaIdx = EntityStatType.getAssetMap().getIndex("Mana");
        if (manaIdx < 0) {
            context.sendMessage(Message.raw("Mana stat type not found."));
            return;
        }

        // Remove old modifier if present, then apply new one
        statMap.removeModifier(manaIdx, MODIFIER_KEY);
        if (amount > 0) {
            statMap.putModifier(manaIdx, MODIFIER_KEY,
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, amount));
        }

        float currentMana = statMap.get(manaIdx).get();
        float maxMana = statMap.get(manaIdx).getMax();
        context.sendMessage(Message.raw("Mana pool set: +" + amount + " max mana (current=" + (int) currentMana + " max=" + (int) maxMana + ")"));
    }
}
