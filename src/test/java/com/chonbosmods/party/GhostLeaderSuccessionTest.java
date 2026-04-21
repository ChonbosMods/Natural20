package com.chonbosmods.party;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ghost-leader rule: if the current leader has been offline continuously
 * for longer than the threshold, leadership transfers to the next online
 * member on their next login.
 *
 * <p>The rule only fires for ghost leaders. A leader who is offline but under
 * the threshold stays leader (the "no auto-transfer on disconnect" invariant).
 */
class GhostLeaderSuccessionTest {

    @Test
    void ghostLeaderTransfersLeadershipWhenOfflineLongerThanThreshold() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-21T00:00:00Z"));
        Nat20PartyRegistry reg = new Nat20PartyRegistry(now::get, Duration.ofDays(7));

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String pid = reg.getParty(alice).getPartyId();
        reg.acceptInvite(bob, pid);

        reg.markOffline(alice);

        now.set(Instant.parse("2026-04-29T00:00:00Z")); // 8 days later
        reg.markOnline(bob);

        assertEquals(bob, reg.getParty(bob).getLeader(),
            "after 8 days offline alice, bob logging in promotes him");
    }

    @Test
    void ghostLeaderRuleDoesNotFireBeforeThreshold() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-21T00:00:00Z"));
        Nat20PartyRegistry reg = new Nat20PartyRegistry(now::get, Duration.ofDays(7));

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

        reg.markOffline(alice);

        now.set(Instant.parse("2026-04-27T00:00:00Z")); // 6 days later
        reg.markOnline(bob);

        assertEquals(alice, reg.getParty(bob).getLeader(),
            "under threshold the leader stays alice");
    }

    @Test
    void ghostLeaderRuleDoesNotFireIfLeaderCurrentlyOnline() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-21T00:00:00Z"));
        Nat20PartyRegistry reg = new Nat20PartyRegistry(now::get, Duration.ofDays(7));

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

        // Alice is online currently, even though her last seen was long ago.
        reg.markOnline(alice);

        now.set(Instant.parse("2026-05-21T00:00:00Z")); // 30 days later
        reg.markOnline(bob);

        assertEquals(alice, reg.getParty(bob).getLeader(),
            "alice is online so bob cannot inherit even after a long gap");
    }

    @Test
    void ghostLeaderRuleDoesNotFireInSoloParty() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-21T00:00:00Z"));
        Nat20PartyRegistry reg = new Nat20PartyRegistry(now::get, Duration.ofDays(7));

        UUID alice = UUID.randomUUID();
        reg.getParty(alice);
        reg.markOffline(alice);

        now.set(Instant.parse("2026-05-21T00:00:00Z"));
        reg.markOnline(alice);

        assertEquals(alice, reg.getParty(alice).getLeader(),
            "in a solo party alice is the only possible leader");
    }

    @Test
    void ghostLeaderPromotesInJoinOrderWhenMultipleMembersPresent() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-21T00:00:00Z"));
        Nat20PartyRegistry reg = new Nat20PartyRegistry(now::get, Duration.ofDays(7));

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        String pid = reg.getParty(alice).getPartyId();
        reg.acceptInvite(bob, pid);
        reg.acceptInvite(carol, pid);

        reg.markOffline(alice);

        now.set(Instant.parse("2026-04-29T00:00:00Z")); // 8 days later

        // Carol logs in first. She is later in join order than bob, so per the
        // rule the next-by-join-order online member becomes leader, which is
        // carol (because bob is offline at this moment).
        reg.markOnline(carol);

        assertEquals(carol, reg.getParty(carol).getLeader(),
            "the first online non-leader to log in after the threshold becomes leader");
    }
}
