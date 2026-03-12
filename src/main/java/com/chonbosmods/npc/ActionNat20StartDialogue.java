package com.chonbosmods.npc;

import com.chonbosmods.dialogue.DialogueManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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

        dialogueManager.startSession(playerRef, npcRef, store);
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
