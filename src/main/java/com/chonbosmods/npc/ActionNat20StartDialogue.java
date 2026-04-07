package com.chonbosmods.npc;

import com.chonbosmods.dialogue.DialogueManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hypixel.hytale.server.npc.util.NPCPhysicsMath;

public class ActionNat20StartDialogue extends ActionBase {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|StartDialogue");

    private final DialogueManager dialogueManager;

    public ActionNat20StartDialogue(BuilderActionNat20StartDialogue builder,
                                     DialogueManager dialogueManager) {
        super(builder);
        this.dialogueManager = dialogueManager;
    }

    @Override
    public boolean canExecute(Ref<EntityStore> npcRef, Role role,
                               InfoProvider info, double dt,
                               Store<EntityStore> store) {
        return true;
    }

    @Override
    public boolean execute(Ref<EntityStore> npcRef, Role role,
                            InfoProvider info, double dt,
                            Store<EntityStore> store) {
        Ref<EntityStore> playerRef = role.getStateSupport()
                .getInteractionIterationTarget();

        if (playerRef == null) {
            LOGGER.atWarning().log("Nat20StartDialogue fired with no interaction target");
            return true;
        }

        // Snap NPC body rotation to face the player. One-time only: the player
        // is movement-locked during dialogue, so we don't need a continuous ticker.
        // Past attempts at per-tick rotation caused jitter and broke flee transitions.
        try {
            TransformComponent npcTransform =
                    store.getComponent(npcRef, TransformComponent.getComponentType());
            TransformComponent playerTransform =
                    store.getComponent(playerRef, TransformComponent.getComponentType());
            if (npcTransform != null && playerTransform != null) {
                Vector3d npcPos = npcTransform.getPosition();
                Vector3d playerPos = playerTransform.getPosition();
                double dx = playerPos.getX() - npcPos.getX();
                double dz = playerPos.getZ() - npcPos.getZ();
                Vector3f currentRotation = npcTransform.getRotation();
                float newYaw = NPCPhysicsMath.headingFromDirection(
                        dx, dz, currentRotation.getYaw());
                // Vector3f rotation order: (pitch, yaw, roll) == (x, y, z).
                // Preserve pitch and roll, only update yaw.
                npcTransform.teleportRotation(new Vector3f(
                        currentRotation.getPitch(), newYaw, currentRotation.getRoll()));
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e)
                    .log("Failed to snap NPC rotation toward player at dialogue start");
            // Non-fatal: dialogue still proceeds, NPC just keeps its current facing.
        }

        // Create cleanup callback to release NPC from $Interaction when dialogue ends
        Runnable releaseNpc = () -> {
            try {
                int idleIndex = role.getStateSupport().getStateHelper().getStateIndex("Idle");
                role.getStateSupport().setState(idleIndex, 0, false, false);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to release NPC from $Interaction");
            }
        };

        dialogueManager.startSession(playerRef, npcRef, store, releaseNpc);
        return true;
    }

    @Override
    public void activate(Role role, InfoProvider info) {
    }

    @Override
    public void deactivate(Role role, InfoProvider info) {
    }

    @Override
    public boolean isActivated() {
        return false;
    }
}
