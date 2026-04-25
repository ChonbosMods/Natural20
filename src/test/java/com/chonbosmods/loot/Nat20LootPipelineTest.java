package com.chonbosmods.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Nat20LootPipelineTest {

    @Test
    void rarityGateIsFullRangeAtAllIlvls() {
        // Pre-change: ilvl 1 returns {1,3}, ilvl 9 returns {1,4}, ilvl 16 returns {2,5},
        // ilvl 26+ returns {1,5}. Post-change: every ilvl returns {1,5}.
        for (int ilvl : new int[]{1, 5, 8, 9, 15, 16, 25, 26, 30, 45}) {
            assertArrayEquals(new int[]{1, 5}, Nat20LootPipeline.rarityGateForIlvl(ilvl),
                "ilvl=" + ilvl + " must return {1, 5} after gate removal");
        }
    }
}
