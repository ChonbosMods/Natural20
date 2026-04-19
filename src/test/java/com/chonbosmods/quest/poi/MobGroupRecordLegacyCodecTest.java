package com.chonbosmods.quest.poi;

import com.chonbosmods.progression.GroupSource;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobGroupRecordLegacyCodecTest {

    @Test
    void legacyJsonWithoutSourceFieldDeserializesToNullSource() {
        String legacyJson = "{\"groupKey\":\"poi:abc:q1:0\",\"createdAtMillis\":12345}";
        MobGroupRecord record = new Gson().fromJson(legacyJson, MobGroupRecord.class);
        assertEquals("poi:abc:q1:0", record.getGroupKey());
        assertNull(record.getSource(), "legacy records should deserialize source as null");
        assertEquals(12345L, record.getLastSeenMillis(),
                "legacy records without lastSeenMillis should fall back to createdAtMillis");
    }

    @Test
    void newRecordRoundTrips() {
        MobGroupRecord r = new MobGroupRecord();
        r.setGroupKey("ambient:seed:0:0:999");
        r.setSource(GroupSource.AMBIENT);
        r.setCreatedAtMillis(1000L);
        r.setLastSeenMillis(2000L);
        Gson gson = new Gson();
        MobGroupRecord round = gson.fromJson(gson.toJson(r), MobGroupRecord.class);
        assertEquals(GroupSource.AMBIENT, round.getSource());
        assertEquals(2000L, round.getLastSeenMillis());
    }
}
