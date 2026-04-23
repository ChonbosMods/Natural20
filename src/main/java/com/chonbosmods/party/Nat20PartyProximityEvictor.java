package com.chonbosmods.party;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pure function: given the eligible-accepters set, the triggering player's uuid + anchor
 * position, and a position resolver, returns the set of accepters that should be evicted.
 *
 * Offline accepters (positionResolver returns empty) are evicted.
 * The triggering player is never evicted, even if their position resolver fails.
 * Distance check is inclusive at the radius.
 */
public final class Nat20PartyProximityEvictor {
    private Nat20PartyProximityEvictor() {}

    public static Set<UUID> sweep(
            Set<UUID> eligibleAccepters,
            UUID triggeringPlayer,
            double[] anchorXyz,
            Function<UUID, Optional<double[]>> positionResolver,
            double radius) {
        Set<UUID> evicted = new HashSet<>();
        double r2 = radius * radius;
        for (UUID uuid : eligibleAccepters) {
            if (uuid.equals(triggeringPlayer)) continue;
            Optional<double[]> pos = positionResolver.apply(uuid);
            if (pos.isEmpty()) { evicted.add(uuid); continue; }
            double[] p = pos.get();
            double dx = p[0] - anchorXyz[0];
            double dy = p[1] - anchorXyz[1];
            double dz = p[2] - anchorXyz[2];
            if (dx*dx + dy*dy + dz*dz > r2) evicted.add(uuid);
        }
        return evicted;
    }
}
