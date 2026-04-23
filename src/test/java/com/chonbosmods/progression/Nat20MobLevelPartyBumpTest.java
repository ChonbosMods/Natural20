package com.chonbosmods.progression;

import com.hypixel.hytale.codec.ExtraInfo;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Nat20MobLevelPartyBumpTest {

    @Test
    void freshInstanceDefaultsToZero() {
        Nat20MobLevel level = new Nat20MobLevel();
        assertEquals(0, level.getPartyBump());
    }

    @Test
    void setterClampsNegativeToZero() {
        Nat20MobLevel level = new Nat20MobLevel();
        level.setPartyBump(-5);
        assertEquals(0, level.getPartyBump(),
                "negative inputs are malformed callers; clamp defensively");
    }

    @Test
    void partyBumpSurvivesCodecRoundtrip() {
        Nat20MobLevel original = new Nat20MobLevel();
        original.setAreaLevel(12);
        original.setTier(Tier.CHAMPION);
        original.setScaled(true);
        original.setDifficultyTier(DifficultyTier.RARE);
        original.setPartyBump(4);

        BsonValue encoded = Nat20MobLevel.CODEC.encode(original, ExtraInfo.THREAD_LOCAL.get());
        Nat20MobLevel roundtripped =
                Nat20MobLevel.CODEC.decode(encoded, ExtraInfo.THREAD_LOCAL.get());

        assertEquals(4, roundtripped.getPartyBump());
        assertEquals(12, roundtripped.getAreaLevel());
        assertEquals(Tier.CHAMPION, roundtripped.getTier());
    }

    @Test
    void partyBumpDefaultsZeroWhenKeyAbsent() {
        // Simulates legacy chunk data written before the PartyBump key existed:
        // an empty document decodes into a level whose bump is still 0.
        BsonDocument empty = new BsonDocument();
        Nat20MobLevel decoded = Nat20MobLevel.CODEC.decode(empty, ExtraInfo.THREAD_LOCAL.get());
        assertEquals(0, decoded.getPartyBump());
    }

    @Test
    void cloneCopiesPartyBump() {
        Nat20MobLevel original = new Nat20MobLevel();
        original.setPartyBump(3);

        Nat20MobLevel copy = original.clone();
        assertEquals(3, copy.getPartyBump());
    }
}
