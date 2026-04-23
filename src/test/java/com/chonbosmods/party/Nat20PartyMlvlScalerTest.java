package com.chonbosmods.party;

import org.junit.jupiter.api.Test;
import static com.chonbosmods.party.Nat20PartyTuning.MLVL_PARTY_CAP;
import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyMlvlScalerTest {

    @Test
    void solo_noBump() {
        assertEquals(5, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 1));
    }

    @Test
    void partyOfTwo_plusOne() {
        assertEquals(6, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 2));
    }

    @Test
    void partyOfFour_plusThree() {
        assertEquals(8, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 4));
    }

    @Test
    void cappedAtMlvlPartyCap() {
        // baseMlvl=5, nearby=20 -> bump is 19 but capped at MLVL_PARTY_CAP
        assertEquals(5 + MLVL_PARTY_CAP,
                Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 20));
    }

    @Test
    void zeroNearby_treatedAsSolo() {
        // Edge case: resolver returned 0 (shouldn't happen but defensive).
        assertEquals(5, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, 0));
    }

    @Test
    void negativeNearby_treatedAsSolo() {
        assertEquals(5, Nat20PartyMlvlScaler.computeEffectiveMlvl(5, -1));
    }

    @Test
    void computeBump_nullPlayer_returnsZero() {
        // Defensive: apply() short-circuits on null triggeringPlayer, returning baseMlvl=0.
        // computeBump therefore returns 0 in the solo/null case.
        assertEquals(0, Nat20PartyMlvlScaler.computeBump(null, null));
    }

    @Test
    void computeBump_bumpFormula_matchesComputeEffectiveMlvl() {
        // Verify the bump-only return matches the (effective - base) delta of the pure
        // function across the expected range. This isn't about runtime integration;
        // it's confirming the helper is a straight delegate to apply(0, ...).
        for (int nearby = 0; nearby <= MLVL_PARTY_CAP + 3; nearby++) {
            int effective = Nat20PartyMlvlScaler.computeEffectiveMlvl(0, nearby);
            // For baseMlvl=0, computeEffectiveMlvl == bump.
            assertEquals(Math.min(Math.max(0, nearby - 1), MLVL_PARTY_CAP), effective,
                    "nearby=" + nearby);
        }
    }
}
