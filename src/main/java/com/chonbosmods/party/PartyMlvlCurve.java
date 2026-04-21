package com.chonbosmods.party;

/**
 * Party-size mlvl scaling curve. Applied at spawn time to the base mlvl of a
 * nat20 mob group based on the number of online party members near the spawn
 * trigger.
 *
 * <p>Starting shape is linear: {@code mlvl_effective = baseMlvl + (n - 1)}.
 * A diminishing-returns curve is expected after playtest tuning; this class
 * is the single chokepoint to update when that happens.
 *
 * <p>Design reference: {@code docs/plans/2026-04-21-party-multiplayer-quest-design.md}
 * §6 Monster level scaling.
 */
public final class PartyMlvlCurve {

    private PartyMlvlCurve() {}

    public static int apply(int baseMlvl, int nearbyPartyMembers) {
        int n = Math.max(1, nearbyPartyMembers);
        return baseMlvl + (n - 1);
    }
}
