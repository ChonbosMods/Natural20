package com.chonbosmods.party;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    void leaderCanKickNonLeaderMember() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

        reg.kick(alice, bob);

        assertEquals(List.of(alice), reg.getParty(alice).getMembers());
        assertTrue(reg.getParty(bob).isSolo());
    }

    @Test
    void nonLeaderCannotKick() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        String pid = reg.getParty(alice).getPartyId();
        reg.acceptInvite(bob, pid);
        reg.acceptInvite(carol, pid);

        assertThrows(SecurityException.class, () -> reg.kick(bob, carol));
    }

    @Test
    void cannotKickSelf() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

        assertThrows(IllegalArgumentException.class, () -> reg.kick(alice, alice));
    }

    @Test
    void kickingNonMemberThrows() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        reg.getParty(alice);
        reg.getParty(stranger);

        assertThrows(IllegalArgumentException.class, () -> reg.kick(alice, stranger));
    }

    @Test
    void saveAndLoadRoundTripsMultiMemberPartyCompositionAndLeader(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("parties.json");

        Nat20PartyRegistry out = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        String pid = out.getParty(alice).getPartyId();
        out.acceptInvite(bob, pid);
        out.acceptInvite(carol, pid);

        out.saveTo(file);

        Nat20PartyRegistry in = new Nat20PartyRegistry();
        in.loadFrom(file);

        Nat20Party restored = in.getParty(alice);
        assertEquals(List.of(alice, bob, carol), restored.getMembers());
        assertEquals(alice, restored.getLeader());
        assertEquals(pid, restored.getPartyId());
        assertSame(restored, in.getParty(bob));
        assertSame(restored, in.getParty(carol));
    }

    @Test
    void saveAndLoadPreservesLastSeenForGhostLeaderRule(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("parties.json");

        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-01T00:00:00Z"));
        Nat20PartyRegistry out = new Nat20PartyRegistry(now::get, Duration.ofDays(7));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        out.acceptInvite(bob, out.getParty(alice).getPartyId());
        out.markOffline(alice); // last seen 2026-04-01

        out.saveTo(file);

        // Reload in a fresh registry. Clock advances past the ghost threshold.
        now.set(Instant.parse("2026-04-15T00:00:00Z")); // 14 days later
        Nat20PartyRegistry in = new Nat20PartyRegistry(now::get, Duration.ofDays(7));
        in.loadFrom(file);

        in.markOnline(bob);

        assertEquals(bob, in.getParty(bob).getLeader(),
            "after reload, ghost-leader rule must still use the persisted lastSeen for alice");
    }

    @Test
    void loadFromMissingFileStartsEmpty(@TempDir Path tmp) throws Exception {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        reg.loadFrom(tmp.resolve("nope.json"));
        UUID alice = UUID.randomUUID();
        // Registry is empty; lookup still auto-creates solo party.
        assertTrue(reg.getParty(alice).isSolo());
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
