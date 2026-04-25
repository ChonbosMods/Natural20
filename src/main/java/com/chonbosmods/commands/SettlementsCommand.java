package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Map;

public class SettlementsCommand extends AbstractPlayerCommand {

    public SettlementsCommand() {
        super("settlements", "List nearby settlements sorted by distance");
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

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d playerPos = transform != null ? transform.getPosition() : new Vector3d(0, 0, 0);

        Map<String, SettlementRecord> all = registry.getAll();
        context.sendMessage(Message.raw("Settlements: " + all.size() + " total (showing nearest 10)"));

        all.values().stream()
            .sorted(Comparator.comparingDouble(r -> {
                double dx = r.getPosX() - playerPos.getX();
                double dz = r.getPosZ() - playerPos.getZ();
                return dx * dx + dz * dz;
            }))
            .limit(10)
            .forEach(r -> {
                double dx = r.getPosX() - playerPos.getX();
                double dz = r.getPosZ() - playerPos.getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);

                // Use NPC Y if settlement Y is 0 (legacy records)
                int displayY = (int) r.getPosY();
                if (displayY == 0 && !r.getNpcs().isEmpty()) {
                    displayY = (int) r.getNpcs().getFirst().getSpawnY();
                }

                context.sendMessage(Message.raw("  " + r.getCellKey() +
                    " [" + r.getType() + "] " +
                    (int) r.getPosX() + " " + displayY + " " + (int) r.getPosZ() +
                    " (" + dist + "m)"));
            });
    }
}
