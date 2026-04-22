package com.chonbosmods.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Nat20PlayerDataDefaultsTest {
    @Test
    void newPlayerDataHasAllZeroStats() {
        Nat20PlayerData data = new Nat20PlayerData();
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0}, data.getStats());
    }
}
