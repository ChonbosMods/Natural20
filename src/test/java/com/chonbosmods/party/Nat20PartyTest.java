package com.chonbosmods.party;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyTest {

    @Test
    void soloPartyHasLeaderAsOnlyMember() {
        UUID alice = UUID.randomUUID();
        Nat20Party p = Nat20Party.ofSolo(alice);
        assertEquals(alice, p.getLeader());
        assertEquals(List.of(alice), p.getMembers());
        assertTrue(p.isSolo());
    }

    @Test
    void soloPartyHasNonNullIdentifier() {
        UUID alice = UUID.randomUUID();
        Nat20Party p = Nat20Party.ofSolo(alice);
        assertNotNull(p.getPartyId());
        assertFalse(p.getPartyId().isEmpty());
    }

    @Test
    void twoSoloPartiesHaveDistinctIds() {
        Nat20Party a = Nat20Party.ofSolo(UUID.randomUUID());
        Nat20Party b = Nat20Party.ofSolo(UUID.randomUUID());
        assertNotEquals(a.getPartyId(), b.getPartyId());
    }

    @Test
    void membersAreJoinOrdered() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Nat20Party p = Nat20Party.ofSolo(a);
        p.addMember(b);
        p.addMember(c);
        assertEquals(List.of(a, b, c), p.getMembers());
        assertFalse(p.isSolo());
    }

    @Test
    void addMemberIsIdempotent() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Nat20Party p = Nat20Party.ofSolo(a);
        p.addMember(b);
        p.addMember(b);
        assertEquals(List.of(a, b), p.getMembers());
    }

    @Test
    void getMembersReturnsUnmodifiableView() {
        UUID a = UUID.randomUUID();
        Nat20Party p = Nat20Party.ofSolo(a);
        List<UUID> members = p.getMembers();
        assertThrows(UnsupportedOperationException.class,
            () -> members.add(UUID.randomUUID()),
            "getMembers must not expose the mutable internal list");
    }
}
