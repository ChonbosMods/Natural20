package com.chonbosmods.background;

import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.stats.Stat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundCommitterStatsTest {

    @Test
    void soldierAddsThreeToStrAndCon() {
        Nat20PlayerData data = new Nat20PlayerData();
        BackgroundCommitter.applyStats(data, Background.SOLDIER);

        int[] stats = data.getStats();
        assertEquals(3, stats[Stat.STR.index()]);
        assertEquals(3, stats[Stat.CON.index()]);
        assertEquals(0, stats[Stat.DEX.index()]);
        assertEquals(0, stats[Stat.INT.index()]);
        assertEquals(0, stats[Stat.WIS.index()]);
        assertEquals(0, stats[Stat.CHA.index()]);
    }

    @Test
    void sageAddsThreeToIntAndWis() {
        Nat20PlayerData data = new Nat20PlayerData();
        BackgroundCommitter.applyStats(data, Background.SAGE);

        int[] stats = data.getStats();
        assertEquals(3, stats[Stat.INT.index()]);
        assertEquals(3, stats[Stat.WIS.index()]);
        assertEquals(0, stats[Stat.STR.index()]);
        assertEquals(0, stats[Stat.DEX.index()]);
        assertEquals(0, stats[Stat.CON.index()]);
        assertEquals(0, stats[Stat.CHA.index()]);
    }

    @Test
    void applyStatsSetsFirstJoinSeen() {
        Nat20PlayerData data = new Nat20PlayerData();
        assertFalse(data.isFirstJoinSeen(), "precondition: flag starts false");

        BackgroundCommitter.applyStats(data, Background.ACOLYTE);

        assertTrue(data.isFirstJoinSeen());
    }

    @Test
    void applyStatsAddsOnTopOfExistingStats() {
        // If applyStats is somehow called a second time (e.g., tester scenario),
        // it should ADD +3/+3, not RESET to +3/+3. The firstJoinSeen guard in the
        // PlayerReadyEvent hook (Task 4.1) is the real protection against double-apply,
        // but this test locks the additive semantics to prevent surprises.
        Nat20PlayerData data = new Nat20PlayerData();
        data.setStats(new int[]{5, 0, 0, 0, 0, 0});

        BackgroundCommitter.applyStats(data, Background.SOLDIER);

        int[] stats = data.getStats();
        assertEquals(8, stats[Stat.STR.index()]);  // 5 + 3
        assertEquals(3, stats[Stat.CON.index()]);  // 0 + 3
    }
}
