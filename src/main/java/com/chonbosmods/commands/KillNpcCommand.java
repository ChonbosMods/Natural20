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
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Kills the nearest settlement NPC within 10 blocks for testing the respawn system.
 * Removes invulnerability and sets health to 0.
 */
public class KillNpcCommand extends AbstractPlayerCommand {

    private static final double MAX_DISTANCE = 10.0;

    public KillNpcCommand() {
        super("killnpc", "Kill the nearest settlement NPC (for testing respawn)");
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

        // Find closest alive settlement NPC within range
        NpcRecord closest = null;
        double closestDist = Double.MAX_VALUE;

        for (SettlementRecord settlement : registry.getAll().values()) {
            for (NpcRecord npc : settlement.getNpcs()) {
                UUID uuid = npc.getEntityUUID();
                if (uuid == null) continue; // dead NPC

                double dx = npc.getSpawnX() - playerPos.getX();
                double dy = npc.getSpawnY() - playerPos.getY();
                double dz = npc.getSpawnZ() - playerPos.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist < closestDist && dist <= MAX_DISTANCE) {
                    closestDist = dist;
                    closest = npc;
                }
            }
        }

        if (closest == null) {
            context.sendMessage(Message.raw("No settlement NPC found within " + (int) MAX_DISTANCE + " blocks."));
            return;
        }

        UUID npcUUID = closest.getEntityUUID();
        String name = closest.getGeneratedName();
        String role = closest.getRole();

        world.execute(() -> {
            Ref<EntityStore> npcRef = world.getEntityRef(npcUUID);
            if (npcRef == null || !npcRef.isValid()) {
                context.sendMessage(Message.raw("NPC entity not found in world: " + name));
                return;
            }

            Store<EntityStore> worldStore = world.getEntityStore().getStore();

            // Remove invulnerability so damage/death can occur
            worldStore.tryRemoveComponent(npcRef, Invulnerable.getComponentType());

            // Set health to 0
            EntityStatMap statMap = worldStore.getComponent(npcRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                context.sendMessage(Message.raw("NPC has no stat map: " + name));
                return;
            }

            int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
            if (healthIndex < 0) {
                context.sendMessage(Message.raw("Health stat not found in asset map."));
                return;
            }

            statMap.minimizeStatValue(healthIndex);

            context.sendMessage(Message.raw("Killed " + name + " (" + role + "): " +
                "removed invulnerability, health set to 0. Respawn should trigger in 30s."));
        });
    }
}
