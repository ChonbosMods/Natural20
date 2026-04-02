package com.chonbosmods.dialogue.model;

import com.chonbosmods.dialogue.ValenceType;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.stats.Stat;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public sealed interface DialogueNode {

    List<Map<String, String>> onEnter();

    record DialogueTextNode(
        String speakerText,
        @Nullable List<String> reactionPool,
        List<ResponseOption> responses,
        List<Map<String, String>> onEnter,
        boolean exhaustsTopic,
        boolean locksConversation,
        @Nullable ValenceType valence
    ) implements DialogueNode {}

    record SkillCheckNode(
        Skill skill,
        @Nullable Stat stat,
        int baseDC,
        boolean dispositionScaling,
        String passNodeId,
        String failNodeId,
        List<Map<String, String>> onEnter
    ) implements DialogueNode {}

    record ActionNode(
        List<Map<String, String>> actions,
        @Nullable String nextNodeId,
        List<Map<String, String>> onEnter,
        boolean exhaustsTopic
    ) implements DialogueNode {}

    record TerminalNode(
        List<Map<String, String>> onEnter,
        boolean exhaustsTopic
    ) implements DialogueNode {}
}
