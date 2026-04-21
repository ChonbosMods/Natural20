package com.chonbosmods.party;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyInviteRegistryTest {

    @Test
    void putAndGetByInviteeRoundTrip() {
        Nat20PartyInviteRegistry reg = new Nat20PartyInviteRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        PartyInvite invite = new PartyInvite(alice, bob, "party-1", Instant.parse("2026-04-21T00:00:00Z"));

        reg.put(invite);

        assertEquals(invite, reg.getForInvitee(bob));
    }

    @Test
    void getForInviteeReturnsNullWhenNoPendingInvite() {
        Nat20PartyInviteRegistry reg = new Nat20PartyInviteRegistry();
        assertNull(reg.getForInvitee(UUID.randomUUID()));
    }

    @Test
    void rePuttingForSameInviteeOverwritesPrior() {
        Nat20PartyInviteRegistry reg = new Nat20PartyInviteRegistry();
        UUID alice = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        PartyInvite first = new PartyInvite(alice, bob, "party-a", Instant.parse("2026-04-21T00:00:00Z"));
        PartyInvite second = new PartyInvite(carol, bob, "party-c", Instant.parse("2026-04-21T01:00:00Z"));

        reg.put(first);
        reg.put(second);

        assertEquals(second, reg.getForInvitee(bob), "one pending invite per invitee; latest wins");
    }

    @Test
    void removeDeletesTheInvite() {
        Nat20PartyInviteRegistry reg = new Nat20PartyInviteRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        reg.put(new PartyInvite(alice, bob, "p", Instant.now()));

        reg.removeForInvitee(bob);

        assertNull(reg.getForInvitee(bob));
    }

    @Test
    void getSentByReturnsAllInvitesFromGivenInviter() {
        Nat20PartyInviteRegistry reg = new Nat20PartyInviteRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        UUID dave = UUID.randomUUID();

        reg.put(new PartyInvite(alice, bob, "p", Instant.now()));
        reg.put(new PartyInvite(alice, carol, "p", Instant.now()));
        reg.put(new PartyInvite(dave, bob, "other", Instant.now())); // overwrites alice→bob

        List<PartyInvite> aliceSent = reg.getSentBy(alice);

        assertEquals(1, aliceSent.size());
        assertEquals(carol, aliceSent.get(0).inviteeUuid());
    }

    @Test
    void saveAndLoadRoundTrips(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("party_invites.json");

        Nat20PartyInviteRegistry out = new Nat20PartyInviteRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Instant when = Instant.parse("2026-04-21T00:00:00Z");
        out.put(new PartyInvite(alice, bob, "party-1", when));
        out.saveTo(file);

        Nat20PartyInviteRegistry in = new Nat20PartyInviteRegistry();
        in.loadFrom(file);

        PartyInvite restored = in.getForInvitee(bob);
        assertNotNull(restored);
        assertEquals(alice, restored.inviterUuid());
        assertEquals(bob, restored.inviteeUuid());
        assertEquals("party-1", restored.targetPartyId());
        assertEquals(when, restored.createdAt());
    }

    @Test
    void loadFromMissingFileStartsEmpty(@TempDir Path tmp) throws Exception {
        Nat20PartyInviteRegistry reg = new Nat20PartyInviteRegistry();
        reg.loadFrom(tmp.resolve("absent.json"));
        assertNull(reg.getForInvitee(UUID.randomUUID()));
    }

    @Test
    void countReturnsNumberOfPendingInvites() {
        Nat20PartyInviteRegistry reg = new Nat20PartyInviteRegistry();
        assertEquals(0, reg.count());
        reg.put(new PartyInvite(UUID.randomUUID(), UUID.randomUUID(), "p", Instant.now()));
        reg.put(new PartyInvite(UUID.randomUUID(), UUID.randomUUID(), "p", Instant.now()));
        assertEquals(2, reg.count());
    }
}
