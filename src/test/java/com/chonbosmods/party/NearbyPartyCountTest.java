package com.chonbosmods.party;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NearbyPartyCountTest {

    @Test
    void countsOnlineMembersWithinRadius() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Set<UUID> online = Set.of(a, b, c);
        Map<UUID, Double> dist = Map.of(a, 0.0, b, 40.0, c, 200.0);

        int n = NearbyPartyCount.count(List.of(a, b, c), online, dist::get, 50.0);

        assertEquals(2, n, "a at 0m and b at 40m fall within radius 50m; c at 200m is excluded");
    }

    @Test
    void offlineMembersExcluded() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Set<UUID> online = Set.of(a);
        int n = NearbyPartyCount.count(List.of(a, b), online, p -> 0.0, 50.0);
        assertEquals(1, n);
    }

    @Test
    void missingDistanceExcludes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Set<UUID> online = Set.of(a, b);
        Map<UUID, Double> dist = Map.of(a, 10.0); // b missing
        int n = NearbyPartyCount.count(List.of(a, b), online, dist::get, 50.0);
        assertEquals(1, n, "members with null distance are treated as out-of-range");
    }

    @Test
    void atExactRadiusIsIncluded() {
        UUID a = UUID.randomUUID();
        int n = NearbyPartyCount.count(List.of(a), Set.of(a), p -> 50.0, 50.0);
        assertEquals(1, n, "distance equal to radius is inclusive");
    }

    @Test
    void soloPartyOfOneOnlineReturnsOne() {
        UUID a = UUID.randomUUID();
        int n = NearbyPartyCount.count(List.of(a), Set.of(a), p -> 0.0, 50.0);
        assertEquals(1, n);
    }

    @Test
    void emptyPartyReturnsZero() {
        int n = NearbyPartyCount.count(List.of(), Set.of(), p -> 0.0, 50.0);
        assertEquals(0, n);
    }
}
