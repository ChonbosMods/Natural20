package com.chonbosmods.party;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Counts party members that are both online and within a given radius of a
 * reference point (usually a mob-group spawn trigger). Feeds
 * {@link Nat20PartyMlvlScaler#computeEffectiveMlvl(int, int)}.
 *
 * <p>Distance lookup is injected so callers can source it from whatever the
 * current runtime holds (entity store positions, player refs, etc.) without
 * coupling this helper to a specific ECS shape.
 *
 * <p>Design reference: {@code docs/plans/2026-04-21-party-multiplayer-quest-design.md}
 * §6 Monster level scaling.
 */
public final class NearbyPartyCount {

    private NearbyPartyCount() {}

    /**
     * @param members       all party members (online or offline)
     * @param online        set of currently-online player UUIDs
     * @param distance      member UUID to distance from the reference point;
     *                      {@code null} means "unknown / not resolvable"
     *                      and is treated as out-of-range
     * @param radius        inclusive radius threshold
     */
    public static int count(List<UUID> members, Set<UUID> online,
                            Function<UUID, Double> distance, double radius) {
        int n = 0;
        for (UUID m : members) {
            if (!online.contains(m)) continue;
            Double d = distance.apply(m);
            if (d == null) continue;
            if (d <= radius) n++;
        }
        return n;
    }
}
