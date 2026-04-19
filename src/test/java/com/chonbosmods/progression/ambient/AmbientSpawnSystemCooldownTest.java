package com.chonbosmods.progression.ambient;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmbientSpawnSystemCooldownTest {

    @Test
    void firstRollPassesCooldownGate() {
        AmbientSpawnSystem.CooldownMap cd = new AmbientSpawnSystem.CooldownMap();
        UUID p = UUID.randomUUID();
        assertTrue(cd.canRoll(p, 1000L));
    }

    @Test
    void cooldownBlocksSecondRollBeforeExpiry() {
        AmbientSpawnSystem.CooldownMap cd = new AmbientSpawnSystem.CooldownMap();
        UUID p = UUID.randomUUID();
        cd.markRolled(p, 1000L, 300_000L);
        assertFalse(cd.canRoll(p, 2000L), "second roll 1s later should be blocked");
        assertFalse(cd.canRoll(p, 300_999L), "still blocked just before 5 min");
    }

    @Test
    void cooldownReleasesAfterExpiry() {
        AmbientSpawnSystem.CooldownMap cd = new AmbientSpawnSystem.CooldownMap();
        UUID p = UUID.randomUUID();
        cd.markRolled(p, 1000L, 300_000L);
        assertTrue(cd.canRoll(p, 301_001L));
    }

    @Test
    void playersTrackedIndependently() {
        AmbientSpawnSystem.CooldownMap cd = new AmbientSpawnSystem.CooldownMap();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        cd.markRolled(a, 1000L, 300_000L);
        assertTrue(cd.canRoll(b, 2000L), "player B should not be blocked by player A's cooldown");
    }
}
