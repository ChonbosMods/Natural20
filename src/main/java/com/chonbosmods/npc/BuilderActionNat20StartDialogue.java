package com.chonbosmods.npc;

import com.chonbosmods.dialogue.DialogueManager;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

public class BuilderActionNat20StartDialogue extends BuilderActionBase {

    private final DialogueManager dialogueManager;

    public BuilderActionNat20StartDialogue(DialogueManager dialogueManager) {
        this.dialogueManager = dialogueManager;
    }

    @Override
    public Action build(BuilderSupport support) {
        return new ActionNat20StartDialogue(this, dialogueManager);
    }

    @Override
    public String getShortDescription() {
        return "Starts a Natural20 dialogue session with the interacting player";
    }

    @Override
    public String getLongDescription() {
        return getShortDescription();
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}
