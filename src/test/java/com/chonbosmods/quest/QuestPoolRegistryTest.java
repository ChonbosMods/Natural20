package com.chonbosmods.quest;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class QuestPoolRegistryTest {

    private QuestPoolRegistry loadFixture() {
        QuestPoolRegistry reg = new QuestPoolRegistry();
        Path dir = Paths.get("src/test/resources/fixtures");
        reg.loadTestCollectPool(dir.resolve("quest-pool-tier.json"));
        return reg;
    }

    @Test
    void tierFieldParsed() {
        QuestPoolRegistry reg = loadFixture();
        assertEquals(1, reg.findCollectById("Tier1_Item").tier());
        assertEquals(2, reg.findCollectById("Tier2_Item").tier());
        assertEquals(3, reg.findCollectById("Tier3_Item").tier());
        assertEquals(4, reg.findCollectById("Tier4_Item").tier());
    }

    @Test
    void missingTierDefaultsToOne() {
        QuestPoolRegistry reg = loadFixture();
        assertEquals(1, reg.findCollectById("NoTier_Item").tier(),
            "entries without tier must default to 1 (starter-safe)");
    }

    @Test
    void tierOutOfRangeDefaultsToOne() {
        QuestPoolRegistry reg = loadFixture();
        assertEquals(1, reg.findCollectById("TierZero_Item").tier(),
            "tier=0 is outside [1,4] and must default to 1");
        assertEquals(1, reg.findCollectById("TierFive_Item").tier(),
            "tier=5 is outside [1,4] and must default to 1");
    }

    @Test
    void tierNonNumericDefaultsToOne() {
        QuestPoolRegistry reg = loadFixture();
        assertEquals(1, reg.findCollectById("TierString_Item").tier(),
            "non-numeric tier (string) must default to 1");
    }
}
