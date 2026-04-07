package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Teleports the player to the nearest NPC that has an available quest.
 * Scans all settlements for NPCs with a preGeneratedQuest and teleports
 * to the closest one using the built-in /tp command.
 */
public class QuestTpCommand extends AbstractPlayerCommand {

    public QuestTpCommand() {
        super("questtp", "Teleport to the nearest quest-bearing NPC");
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
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d playerPos = transform.getPosition();

        // Find closest NPC with a quest available
        NpcRecord closest = null;
        String closestSettlement = null;
        double closestDist = Double.MAX_VALUE;

        for (SettlementRecord settlement : registry.getAll().values()) {
            for (NpcRecord npc : settlement.getNpcs()) {
                if (npc.getPreGeneratedQuest() == null) continue;

                double dx = npc.getSpawnX() - playerPos.getX();
                double dy = npc.getSpawnY() - playerPos.getY();
                double dz = npc.getSpawnZ() - playerPos.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist < closestDist) {
                    closestDist = dist;
                    closest = npc;
                    closestSettlement = settlement.getName();
                }
            }
        }

        if (closest == null) {
            context.sendMessage(Message.raw("No quest-bearing NPCs found in any settlement."));
            return;
        }

        int x = (int) closest.getSpawnX();
        int y = (int) closest.getSpawnY();
        int z = (int) closest.getSpawnZ();
        String name = closest.getGeneratedName();
        String role = closest.getRole();

        // Dispatch the built-in /tp command
        String tpCommand = "tp " + x + " " + y + " " + z;
        CommandManager.get().handleCommand(context.sender(), tpCommand);

        context.sendMessage(Message.raw("Teleporting to " + name + " (" + role + ") in " +
                closestSettlement + ": " + x + ", " + y + ", " + z +
                " (" + (int) closestDist + " blocks away)"));
    }
}
