package com.chonbosmods.action;

import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.ObjectiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueActionRegistryScaleTest {

    @Test
    void scalesForZone1Player() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "label", 99, null, null);
        obj.setBaseRoll(6);
        obj.setBonusPerZone(4);
        DialogueActionRegistry.rescaleCollectObjective(obj, 1);
        assertEquals(6, obj.getRequiredCount(), "zone 1 player: baseRoll + 4*0");
    }

    @Test
    void scalesForZone4Player() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "label", 99, null, null);
        obj.setBaseRoll(6);
        obj.setBonusPerZone(4);
        DialogueActionRegistry.rescaleCollectObjective(obj, 4);
        assertEquals(18, obj.getRequiredCount(), "zone 4 player: 6 + 4*3");
    }

    @Test
    void nonCollectObjectiveUnchanged() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.KILL_MOBS, "id", "label", 5, null, null);
        obj.setBaseRoll(6);
        obj.setBonusPerZone(4);
        DialogueActionRegistry.rescaleCollectObjective(obj, 4);
        assertEquals(5, obj.getRequiredCount(), "KILL_MOBS rescale must no-op");
    }

    @Test
    void missingBaseRollSkipsRescale() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "label", 12, null, null);
        // baseRoll == 0 means legacy/unmigrated: leave requiredCount alone.
        DialogueActionRegistry.rescaleCollectObjective(obj, 4);
        assertEquals(12, obj.getRequiredCount());
    }

    @Test
    void clampsToOneMinimum() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "label", 99, null, null);
        obj.setBaseRoll(1);
        obj.setBonusPerZone(0);
        DialogueActionRegistry.rescaleCollectObjective(obj, 1);
        assertEquals(1, obj.getRequiredCount(), "never go below 1");
    }
}
