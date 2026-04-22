package com.chonbosmods.progression.ambient;

import com.chonbosmods.progression.GroupSource;
import com.chonbosmods.quest.poi.MobGroupRecord;
import com.chonbosmods.quest.poi.Nat20MobGroupRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AmbientDecaySweepTest {

    private static Nat20MobGroupRegistry newRegistry(Path dir) {
        Nat20MobGroupRegistry reg = new Nat20MobGroupRegistry();
        reg.setSaveDirectory(dir);
        return reg;
    }


    @Test
    void recordOlderThanWindowWithNoPlayerNearIsExpired(@TempDir Path tmp) {
        Nat20MobGroupRegistry reg = newRegistry(tmp);
        long now = 10_000_000L;
        MobGroupRecord r = ambientRecord("ambient:g1", now - 2_000_000L);
        reg.put(r);

        AmbientSpawnConfig cfg = AmbientSpawnConfig.load();
        AmbientSpawnSystem.DecayPolicy policy = new AmbientSpawnSystem.DecayPolicy(cfg);
        List<String> toRemove = policy.selectExpired(reg.ambientRecords(), List.of(), now);
        assertEquals(1, toRemove.size());
        assertEquals("ambient:g1", toRemove.get(0));
    }

    @Test
    void recordWithPlayerNearRefreshesLastSeen(@TempDir Path tmp) {
        Nat20MobGroupRegistry reg = newRegistry(tmp);
        long now = 10_000_000L;
        MobGroupRecord r = ambientRecord("ambient:g1", now - 2_000_000L);
        r.setAnchor(0, 64, 0);
        reg.put(r);

        AmbientSpawnConfig cfg = AmbientSpawnConfig.load();
        AmbientSpawnSystem.DecayPolicy policy = new AmbientSpawnSystem.DecayPolicy(cfg);
        List<AmbientSpawnSystem.PlayerXZ> players = List.of(new AmbientSpawnSystem.PlayerXZ(50, 0));
        policy.refreshLastSeen(reg.ambientRecords(), players, now);
        assertEquals(now, reg.get("ambient:g1").getLastSeenMillis());
        assertTrue(policy.selectExpired(reg.ambientRecords(), players, now).isEmpty());
    }

    @Test
    void youngRecordIsNotExpired(@TempDir Path tmp) {
        Nat20MobGroupRegistry reg = newRegistry(tmp);
        long now = 10_000_000L;
        MobGroupRecord r = ambientRecord("ambient:g1", now - 60_000L);
        reg.put(r);
        AmbientSpawnConfig cfg = AmbientSpawnConfig.load();
        AmbientSpawnSystem.DecayPolicy policy = new AmbientSpawnSystem.DecayPolicy(cfg);
        assertTrue(policy.selectExpired(reg.ambientRecords(), List.of(), now).isEmpty());
    }

    @Test
    void distantPlayerDoesNotRefresh(@TempDir Path tmp) {
        Nat20MobGroupRegistry reg = newRegistry(tmp);
        long now = 10_000_000L;
        MobGroupRecord r = ambientRecord("ambient:g1", now - 2_000_000L);
        r.setAnchor(0, 64, 0);
        reg.put(r);
        AmbientSpawnConfig cfg = AmbientSpawnConfig.load();
        AmbientSpawnSystem.DecayPolicy policy = new AmbientSpawnSystem.DecayPolicy(cfg);
        // Player at (200, 0): outside decayPlayerNearRadius=150.
        List<AmbientSpawnSystem.PlayerXZ> players = List.of(new AmbientSpawnSystem.PlayerXZ(200, 0));
        policy.refreshLastSeen(reg.ambientRecords(), players, now);
        // lastSeenMillis should remain at (now - 2000000L) via the getter fallback.
        assertEquals(now - 2_000_000L, reg.get("ambient:g1").getLastSeenMillis());
        assertEquals(1, policy.selectExpired(reg.ambientRecords(), players, now).size());
    }

    private static MobGroupRecord ambientRecord(String key, long createdAt) {
        MobGroupRecord r = new MobGroupRecord();
        r.setGroupKey(key);
        r.setSource(GroupSource.AMBIENT);
        r.setCreatedAtMillis(createdAt);
        // lastSeenMillis intentionally left 0 to exercise the getter fallback.
        r.setAnchor(0, 64, 0);
        r.setSlots(List.of());
        return r;
    }
}
