package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dialogue.model.DialogueGraph;
import com.chonbosmods.dialogue.model.DialogueNode;
import com.chonbosmods.dialogue.model.ResponseOption;
import com.chonbosmods.dialogue.model.TopicDefinition;
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
 * Teleports the player to the nearest NPC whose smalltalk graph has a skill-check
 * option on the topic's <em>entry node</em> (i.e. the first beat of the chain).
 * Used to reproduce the dice → dialogue stuck-page bug deterministically without
 * having to wander a settlement clicking topics until one happens to put the
 * check on beat 0.
 *
 * <p>Walks {@link SettlementRegistry} → each {@link NpcRecord} → the cached
 * {@link DialogueGraph} (resolved by generated name) → each {@link TopicDefinition}
 * entry node, looking for any {@link ResponseOption} whose {@code skillCheckRef}
 * is non-null. Quest topics also expose a skill-check option on their entry node;
 * those get filtered by short-circuiting on topic ids that start with
 * {@code questoffer_}, leaving only smalltalk hits.
 */
public class SkillCheckTpCommand extends AbstractPlayerCommand {

    public SkillCheckTpCommand() {
        super("skillchecktp", "Teleport to the nearest NPC with a first-beat smalltalk skill check");
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
        String closestTopicLabel = null;
        String closestStat = null;
        double closestDist = Double.MAX_VALUE;
        int hits = 0;

        for (SettlementRecord settlement : registry.getAll().values()) {
            for (NpcRecord npc : settlement.getNpcs()) {
                String name = npc.getGeneratedName();
                if (name == null || name.isEmpty()) continue;

                DialogueGraph graph = Natural20.getInstance().getDialogueLoader().getGraphForNpc(name);
                if (graph == null) continue;

                for (TopicDefinition topic : graph.topics()) {
                    // Quest-injected topics also expose a skill-check option on their entry
                    // node, but they're not the bug the smalltalk skill-check repro path
                    // needs. Filter them out by id prefix.
                    if (topic.id() != null && topic.id().startsWith("questoffer_")) continue;

                    DialogueNode entryNode = graph.getNode(topic.entryNodeId());
                    if (!(entryNode instanceof DialogueNode.DialogueTextNode entryText)) continue;

                    String stat = null;
                    for (ResponseOption opt : entryText.responses()) {
                        if (opt.skillCheckRef() != null) {
                            stat = opt.statPrefix() != null ? opt.statPrefix() : "?";
                            break;
                        }
                    }
                    if (stat == null) continue;

                    hits++;

                    double dx = npc.getSpawnX() - playerPos.getX();
                    double dy = npc.getSpawnY() - playerPos.getY();
                    double dz = npc.getSpawnZ() - playerPos.getZ();
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = npc;
                        closestSettlement = settlement.getName();
                        closestTopicLabel = topic.label() != null ? topic.label() : topic.id();
                        closestStat = stat;
                    }
                }
            }
        }

        if (closest == null) {
            context.sendMessage(Message.raw(
                    "No NPC found with a first-beat smalltalk skill check. "
                            + "Topics need to have rolled the stat-check on beat 0; "
                            + "if all your settlements rolled the check on a later beat, "
                            + "try regenerating chunks or running a fresh world."));
            return;
        }

        int x = (int) closest.getSpawnX();
        int y = (int) closest.getSpawnY();
        int z = (int) closest.getSpawnZ();

        String tpCommand = "tp " + x + " " + y + " " + z;
        CommandManager.get().handleCommand(context.sender(), tpCommand);

        context.sendMessage(Message.raw(String.format(
                "Found %d first-beat skill-check NPC(s); teleporting to nearest: %s (%s) in %s, "
                        + "topic '%s' [%s], at %d,%d,%d (%d blocks away)",
                hits, closest.getGeneratedName(), closest.getRole(),
                closestSettlement, closestTopicLabel, closestStat,
                x, y, z, (int) closestDist)));
    }
}
