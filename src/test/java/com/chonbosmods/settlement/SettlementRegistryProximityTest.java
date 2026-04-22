package com.chonbosmods.settlement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SettlementRegistryProximityTest {

    @Test
    void isNearAnySettlementFindsCentersWithinRadius(@TempDir Path tmp) {
        SettlementRegistry reg = new SettlementRegistry();
        reg.setSaveDirectory(tmp);

        SettlementRecord record = new SettlementRecord(
                "0,0",
                UUID.nameUUIDFromBytes("test-world".getBytes()),
                0.0, 64.0, 0.0,
                SettlementType.TOWN);
        reg.register(record);

        assertTrue(reg.isNearAnySettlement(50, 50, 96),
                "settlement within 96 blocks returns true");
        assertFalse(reg.isNearAnySettlement(500, 500, 96),
                "no settlement within 96 blocks returns false");
    }
}
