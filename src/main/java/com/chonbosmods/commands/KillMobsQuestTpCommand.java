package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.ObjectiveType;
import com.chonbosmods.quest.QuestInstance;
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
 * Teleports the player to the nearest NPC whose pre-generated quest has a
 * KILL_MOBS objective anywhere in its objective chain (not just the first phase).
 * Use when smoke-testing the group-spawn integration and mundane quest rolls
 * are drowning out KILL_MOBS bearers.
 */
public class KillMobsQuestTpCommand extends AbstractPlayerCommand {

    public KillMobsQuestTpCommand() {
        super("killmobstp", "Teleport to the nearest NPC whose quest includes a KILL_MOBS phase");
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

        NpcRecord closest = null;
        String closestSettlement = null;
        int closestPhaseIndex = -1;
        double closestDist = Double.MAX_VALUE;

        for (SettlementRecord settlement : registry.getAll().values()) {
            for (NpcRecord npc : settlement.getNpcs()) {
                QuestInstance preQuest = npc.getPreGeneratedQuest();
                if (preQuest == null) continue;

                int killPhase = findKillMobsPhase(preQuest);
                if (killPhase < 0) continue;

                double dx = npc.getSpawnX() - playerPos.getX();
                double dy = npc.getSpawnY() - playerPos.getY();
                double dz = npc.getSpawnZ() - playerPos.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist < closestDist) {
                    closestDist = dist;
                    closest = npc;
                    closestSettlement = settlement.getName();
                    closestPhaseIndex = killPhase;
                }
            }
        }

        if (closest == null) {
            context.sendMessage(Message.raw(
                    "No quest bearers with a KILL_MOBS objective found. Try exploring more settlements."));
            return;
        }

        int x = (int) closest.getSpawnX();
        int y = (int) closest.getSpawnY();
        int z = (int) closest.getSpawnZ();
        String name = closest.getGeneratedName();
        String role = closest.getRole();
        QuestInstance q = closest.getPreGeneratedQuest();

        CommandManager.get().handleCommand(context.sender(), "tp " + x + " " + y + " " + z);

        context.sendMessage(Message.raw(
                "Teleporting to " + name + " (" + role + ") in " + closestSettlement
                        + ": quest=" + q.getSituationId()
                        + " killPhase=" + closestPhaseIndex + "/" + q.getObjectives().size()
                        + " @ " + x + "," + y + "," + z
                        + " (" + (int) closestDist + " blocks)"));
    }

    /** Return the objective index of the first KILL_MOBS or KILL_BOSS phase, or -1 if none. */
    private static int findKillMobsPhase(QuestInstance quest) {
        var objs = quest.getObjectives();
        if (objs == null) return -1;
        for (int i = 0; i < objs.size(); i++) {
            ObjectiveInstance obj = objs.get(i);
            if (obj == null) continue;
            ObjectiveType t = obj.getType();
            if (t == ObjectiveType.KILL_MOBS || t == ObjectiveType.KILL_BOSS) return i;
        }
        return -1;
    }
}
