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

    @Test
    void acceptInviteJoinsInviterPartyAndDisposesOldSolo() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        Nat20Party alicesParty = reg.getParty(alice);
        Nat20Party bobsOldParty = reg.getParty(bob);

        reg.acceptInvite(bob, alicesParty.getPartyId());

        assertSame(alicesParty, reg.getParty(bob), "bob now lives in alice's party");
        assertEquals(List.of(alice, bob), reg.getParty(alice).getMembers());
        assertTrue(bobsOldParty.isEmpty(), "bob's old size-1 party must be disposed");
        assertNull(reg.getPartyById(bobsOldParty.getPartyId()),
            "old solo partyId is no longer resolvable");
    }

    @Test
    void acceptInviteRejectsPlayerAlreadyInMultiMemberParty() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();

        reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

        assertThrows(IllegalStateException.class,
            () -> reg.acceptInvite(bob, reg.getParty(carol).getPartyId()),
            "bob is already in alice's party and cannot accept another invite");
    }

    @Test
    void acceptInviteWithUnknownPartyIdThrows() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        reg.getParty(alice);

        assertThrows(IllegalArgumentException.class,
            () -> reg.acceptInvite(alice, "no-such-party"));
    }

    @Test
    void leaveRevertsToFreshSizeOneParty() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

        reg.leave(bob);

        Nat20Party bobsNew = reg.getParty(bob);
        assertTrue(bobsNew.isSolo());
        assertEquals(bob, bobsNew.getLeader());
        assertEquals(List.of(alice), reg.getParty(alice).getMembers());
    }

    @Test
    void leavingPromotesNextMemberToLeaderInOriginalParty() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        String pid = reg.getParty(alice).getPartyId();
        reg.acceptInvite(bob, pid);
        reg.acceptInvite(carol, pid);

        reg.leave(alice);

        Nat20Party remaining = reg.getParty(bob);
        assertEquals(List.of(bob, carol), remaining.getMembers());
        assertEquals(bob, remaining.getLeader(), "succession fires automatically on leader leave");
    }

    @Test
    void leavingWhenAlreadySoloIsNoOpAndReturnsFreshParty() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        Nat20Party before = reg.getParty(alice);

        reg.leave(alice);

        Nat20Party after = reg.getParty(alice);
        assertTrue(after.isSolo());
        assertEquals(alice, after.getLeader());
        // The partyId may or may not change depending on implementation; both are valid.
        // What must hold: alice is in a solo party led by alice after the call.
        assertEquals(List.of(alice), after.getMembers());
        // Quiet the unused warning; before is retained for readability of the test narrative.
        assertNotNull(before);
    }
}
