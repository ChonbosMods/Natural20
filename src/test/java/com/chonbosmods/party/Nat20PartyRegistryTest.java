package com.chonbosmods.party;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyRegistryTest {

    @Test
    void firstLookupAutoCreatesSizeOneParty() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();

        Nat20Party p = reg.getParty(alice);

        assertTrue(p.isSolo());
        assertEquals(alice, p.getLeader());
        assertEquals(List.of(alice), p.getMembers());
    }

    @Test
    void secondLookupReturnsSameParty() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        assertSame(reg.getParty(alice), reg.getParty(alice));
    }

    @Test
    void differentPlayersGetDifferentParties() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        assertNotSame(reg.getParty(alice), reg.getParty(bob));
    }

    @Test
    void getPartyByIdReturnsSameInstanceAsGetPartyByPlayer() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        Nat20Party alicesParty = reg.getParty(alice);
        assertSame(alicesParty, reg.getPartyById(alicesParty.getPartyId()));
    }

    @Test
    void getPartyByIdReturnsNullForUnknownId() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        assertNull(reg.getPartyById("does-not-exist"));
    }
}
