package com.chonbosmods.data;

import com.hypixel.hytale.codec.ExtraInfo;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Nat20PlayerDataFirstJoinTest {

    @Test
    void newPlayerHasNotSeenFirstJoin() {
        Nat20PlayerData data = new Nat20PlayerData();
        assertFalse(data.isFirstJoinSeen());
    }

    @Test
    void firstJoinSeenIsMutable() {
        Nat20PlayerData data = new Nat20PlayerData();
        data.setFirstJoinSeen(true);
        assertTrue(data.isFirstJoinSeen());
    }

    @Test
    void firstJoinSeenSurvivesCodecRoundtrip() {
        Nat20PlayerData original = new Nat20PlayerData();
        original.setFirstJoinSeen(true);

        BsonValue encoded = Nat20PlayerData.CODEC.encode(original, ExtraInfo.THREAD_LOCAL.get());
        Nat20PlayerData roundtripped = Nat20PlayerData.CODEC.decode(encoded, ExtraInfo.THREAD_LOCAL.get());

        assertTrue(roundtripped.isFirstJoinSeen());
    }

    @Test
    void firstJoinSeenDefaultsFalseWhenKeyAbsent() {
        // Simulates legacy player data written before the field existed:
        // an empty document should decode into a data object whose flag is still false.
        BsonDocument empty = new BsonDocument();
        Nat20PlayerData decoded = Nat20PlayerData.CODEC.decode(empty, ExtraInfo.THREAD_LOCAL.get());
        assertFalse(decoded.isFirstJoinSeen());
    }
}
