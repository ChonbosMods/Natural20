package com.chonbosmods.quest.model;

import java.util.List;

public record QuestReferenceTemplate(
    String id,
    List<String> compatibleSituations,
    String passiveText,
    String triggerTopicLabel,
    String triggerDialogue,
    List<String> catalystSituations,
    List<String> targetNpcRoles
) {}
