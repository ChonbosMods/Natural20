package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestRewardIlvlTest {

    // ---- Reward formula ----

    @Test
    void rewardLowLevelInHomeZone() {
        // player=3, area=5, bonus=5 (Hard) → clamp(3, 0, 5) + 5 = 8
        assertEquals(8, QuestRewardIlvl.reward(3, 5, 5));
    }

    @Test
    void rewardLowLevelInHardZone_clampedToFloor() {
        // player=5, area=15, bonus=5 → clamp(5, 10, 15) + 5 = 15
        assertEquals(15, QuestRewardIlvl.reward(5, 15, 5));
    }

    @Test
    void rewardPlayerMatchedToArea() {
        // player=15, area=15, bonus=5 → clamp(15, 10, 15) + 5 = 20
        assertEquals(20, QuestRewardIlvl.reward(15, 15, 5));
    }

    @Test
    void rewardHighLevelFarmingStarter_cappedAtAreaCeiling() {
        // player=30, area=5, bonus=5 → clamp(30, 0, 5) + 5 = 10
        assertEquals(10, QuestRewardIlvl.reward(30, 5, 5));
    }

    @Test
    void rewardEasyQuestNoBonus() {
        // player=15, area=15, bonus=0 (Easy) → clamp(15, 10, 15) + 0 = 15
        assertEquals(15, QuestRewardIlvl.reward(15, 15, 0));
    }

    @Test
    void rewardClampedToMaxIlvl45() {
        // player=44, area=44, bonus=5 → 44 + 5 = 49 → clamped to 45
        assertEquals(45, QuestRewardIlvl.reward(44, 44, 5));
    }

    @Test
    void rewardClampedToMinIlvl1() {
        // player=1, area=1, bonus=0 → 1 (already at floor)
        assertEquals(1, QuestRewardIlvl.reward(1, 1, 0));
    }

    @Test
    void rewardLowAreaWideClampWindow() {
        // area=2 means lower-bound = max(1, 2-5) = 1 (defensive floor at MIN_ILVL)
        // player=1, area=2, bonus=0 → clamp(1, 1, 2) + 0 = 1
        assertEquals(1, QuestRewardIlvl.reward(1, 2, 0));
    }

    // ---- Encounter formula ----

    @Test
    void encounterMatchesAreaPlusBonus() {
        // area=15, bonus=2 → 17
        assertEquals(17, QuestRewardIlvl.encounter(15, 2));
    }

    @Test
    void encounterEasyQuestUnchanged() {
        // area=10, bonus=0 → 10
        assertEquals(10, QuestRewardIlvl.encounter(10, 0));
    }

    @Test
    void encounterClampedToMax() {
        // area=43, bonus=5 → 48 → clamped to 45
        assertEquals(45, QuestRewardIlvl.encounter(43, 5));
    }

    @Test
    void encounterClampedToMin() {
        // area=0 (defensive — shouldn't happen but)
        assertEquals(1, QuestRewardIlvl.encounter(0, 0));
    }
}
