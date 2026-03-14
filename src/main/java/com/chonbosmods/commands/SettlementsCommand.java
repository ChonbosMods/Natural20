package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;

public class SettlementsCommand extends AbstractPlayerCommand {

    public SettlementsCommand() {
        super("settlements", "List all placed settlements");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();
        if (registry == null) {
            context.sendMessage(Message.raw("Settlement registry not loaded yet."));
            return;
        }

        Map<String, SettlementRecord> all = registry.getAll();
        context.sendMessage(Message.raw("Settlements: " + all.size()));

        for (var entry : all.entrySet()) {
            SettlementRecord r = entry.getValue();
            long alive = r.getNpcs().stream()
                .filter(n -> n.getEntityUUID() != null).count();
            context.sendMessage(Message.raw("  " + entry.getKey() +
                " [" + r.getType() + "] at " +
                (int) r.getPosX() + "," + (int) r.getPosY() + "," + (int) r.getPosZ() +
                " NPCs: " + alive + "/" + r.getNpcs().size()));
        }
    }
}
