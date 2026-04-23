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
}
