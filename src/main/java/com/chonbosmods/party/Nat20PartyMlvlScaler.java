package com.chonbosmods.party;

/**
 * Party-size mlvl scaling. Pure function form for testability.
 * The wiring-layer call (apply) uses the live PartyRegistry; test code uses
 * computeEffectiveMlvl directly with a hand-picked nearbyCount.
 */
public final class Nat20PartyMlvlScaler {
    private Nat20PartyMlvlScaler() {}

    public static int computeEffectiveMlvl(int baseMlvl, int nearbyCount) {
        int bump = Math.min(Math.max(0, nearbyCount - 1), Nat20PartyTuning.MLVL_PARTY_CAP);
        return baseMlvl + bump;
    }
}
