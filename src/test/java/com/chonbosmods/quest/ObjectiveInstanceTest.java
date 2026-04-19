package com.chonbosmods.quest;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveInstanceTest {

    @Test
    void baseRollAndBonusDefaultZero() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "label", 5, null, null);
        assertEquals(0, obj.getBaseRoll());
        assertEquals(0, obj.getBonusPerZone());
    }

    @Test
    void settersAndGetters() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "label", -1, null, null);
        obj.setBaseRoll(7);
        obj.setBonusPerZone(4);
        assertEquals(7, obj.getBaseRoll());
        assertEquals(4, obj.getBonusPerZone());
    }

    @Test
    void gsonRoundtripPreservesFields() {
        ObjectiveInstance obj = new ObjectiveInstance(
            ObjectiveType.COLLECT_RESOURCES, "id", "label", 19, null, null);
        obj.setBaseRoll(7);
        obj.setBonusPerZone(4);

        Gson gson = new Gson();
        String json = gson.toJson(obj);
        ObjectiveInstance restored = gson.fromJson(json, ObjectiveInstance.class);

        assertEquals(7, restored.getBaseRoll());
        assertEquals(4, restored.getBonusPerZone());
        assertEquals(19, restored.getRequiredCount());
    }

    @Test
    void legacyJsonWithoutNewFieldsDefaultsToZero() {
        String legacyJson = "{\"type\":\"COLLECT_RESOURCES\",\"targetId\":\"id\",\"targetLabel\":\"label\",\"requiredCount\":5,\"currentCount\":0,\"complete\":false}";
        ObjectiveInstance restored = new Gson().fromJson(legacyJson, ObjectiveInstance.class);
        assertEquals(0, restored.getBaseRoll(), "old save data has no baseRoll; default to 0");
        assertEquals(0, restored.getBonusPerZone());
        assertEquals(5, restored.getRequiredCount(), "legacy requiredCount still authoritative");
    }
}
