package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DialogueResolverScaleTest {

    @Test
    void gatherCountReflectsPlayerLevelForCollect() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "wheat", 6, null, null);
        obj.setTargetLabelPlural("wheat");
        obj.setBaseRoll(6);
        obj.setBonusPerZone(4);

        Map<String, String> bindings = new HashMap<>();
        // A lvl-35 player (zone 4) should see 6 + 4*3 = 18 in exposition.
        String rendered = DialogueResolver.resolveQuestText(
            "I need {gather_count} {quest_item}.", bindings, obj, 35);

        assertTrue(rendered.contains("18"), "expected 18 wheat for lvl 35, got: " + rendered);
    }

    @Test
    void gatherCountFallsBackToRequiredCountWhenNoScalingData() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "wheat", 12, null, null);
        obj.setTargetLabelPlural("wheat");
        // baseRoll = 0: legacy / no scaling info. Should use requiredCount directly.

        String rendered = DialogueResolver.resolveQuestText(
            "I need {gather_count} {quest_item}.", new HashMap<>(), obj, 35);

        assertTrue(rendered.contains("12"), "expected legacy requiredCount=12, got: " + rendered);
    }

    @Test
    void playerLevelIgnoredForNonCollect() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.KILL_MOBS, "id", "Goblin", 5, null, null);
        obj.setTargetLabelPlural("Goblins");
        obj.setBaseRoll(6);
        obj.setBonusPerZone(4);

        String rendered = DialogueResolver.resolveQuestText(
            "Kill {kill_count} {enemy_type}.", new HashMap<>(), obj, 35);

        assertTrue(rendered.contains("5"), "KILL_MOBS must use requiredCount: " + rendered);
    }
}
