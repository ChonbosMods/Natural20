package com.chonbosmods.commands;

import com.chonbosmods.combat.CombatDebugSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.util.Random;

public class CombatTestCommand extends AbstractPlayerCommand {

    public CombatTestCommand() {
        super("combattest", "Spawn combat dummies and enable debug logging. Use /nat20 loot for test weapons.");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();

        // Spawn passive Combat Dummy 5 blocks ahead
        boolean passiveSpawned = spawnDummy(context, store, pos,
            new Vector3d(pos.getX() + 5, pos.getY(), pos.getZ()),
            "CombatDummy", "Combat Dummy");

        // Spawn aggressive Attacker Dummy offset to the side
        boolean attackerSpawned = spawnDummy(context, store, pos,
            new Vector3d(pos.getX() + 3, pos.getY(), pos.getZ() + 4),
            "AttackerDummy", "Attacker Dummy");

        // Enable combat debug logging for this player
        CombatDebugSystem.enable(playerRef.getUuid());

        // Confirmation message
        StringBuilder msg = new StringBuilder("Combat test setup:");
        msg.append(passiveSpawned ? " Combat Dummy spawned." : " Combat Dummy failed to spawn.");
        msg.append(attackerSpawned ? " Attacker Dummy spawned." : " Attacker Dummy failed to spawn.");
        msg.append(" Debug logging enabled. Use /nat20 loot for test weapons.");
        context.sendMessage(Message.raw(msg.toString()));
    }

    private boolean spawnDummy(CommandContext context, Store<EntityStore> store,
                               Vector3d playerPos, Vector3d spawnPos,
                               String roleName, String displayName) {
        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
            context.sendMessage(Message.raw("Role '" + roleName + "' not registered in NPCPlugin."));
            return false;
        }

        // Face toward the player
        double dx = playerPos.getX() - spawnPos.getX();
        double dz = playerPos.getZ() - spawnPos.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        Vector3f rotation = new Vector3f(0, yaw, 0);

        Random rng = new Random(System.nanoTime());
        Model model = CosmeticsModule.get().createModel(
            CosmeticsModule.get().generateRandomSkin(rng), 1.0f);

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, model, null);

        if (result != null) {
            store.putComponent(result.first(), Nameplate.getComponentType(), new Nameplate(displayName));
            return true;
        }
        return false;
    }

}
