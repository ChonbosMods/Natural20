package com.chonbosmods.party;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PartyMlvlCurveTest {

    @Test
    void soloAddsNothing() {
        assertEquals(5, PartyMlvlCurve.apply(5, 1));
    }

    @Test
    void partyOfTwoAddsOne() {
        assertEquals(6, PartyMlvlCurve.apply(5, 2));
    }

    @Test
    void linearStartingShapeForSmallParties() {
        for (int n = 1; n <= 5; n++) {
            assertEquals(5 + (n - 1), PartyMlvlCurve.apply(5, n),
                "size " + n + " should add " + (n - 1) + " to base");
        }
    }

    @Test
    void nearbyCountZeroIsTreatedAsSolo() {
        assertEquals(5, PartyMlvlCurve.apply(5, 0));
    }

    @Test
    void negativeNearbyIsClampedToSolo() {
        assertEquals(5, PartyMlvlCurve.apply(5, -3),
            "negative inputs are malformed callers; clamp defensively instead of crashing at a spawn site");
    }

    @Test
    void baseMlvlIsPreservedExactly() {
        assertEquals(1, PartyMlvlCurve.apply(1, 1));
        assertEquals(42, PartyMlvlCurve.apply(42, 1));
    }
}
