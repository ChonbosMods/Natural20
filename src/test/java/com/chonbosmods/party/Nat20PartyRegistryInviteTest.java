package com.chonbosmods.party;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Nat20PartyRegistryInviteTest {

    @Test
    void isOnlineReflectsMarkOnlineMarkOffline() {
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        assertFalse(reg.isOnline(alice));
        reg.markOnline(alice);
        assertTrue(reg.isOnline(alice));
        reg.markOffline(alice);
        assertFalse(reg.isOnline(alice));
    }

    @Test
    void createInviteProducesPendingInviteForInvitee() {
        Nat20PartyInviteRegistry invites = new Nat20PartyInviteRegistry();
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String pid = reg.getParty(alice).getPartyId();

        reg.createInvite(invites, alice, bob);

        PartyInvite invite = invites.getForInvitee(bob);
        assertNotNull(invite);
        assertEquals(alice, invite.inviterUuid());
        assertEquals(bob, invite.inviteeUuid());
        assertEquals(pid, invite.targetPartyId(),
            "invite captures the inviter's current party id at send time");
    }

    @Test
    void createInviteRejectsSelfInvite() {
        Nat20PartyInviteRegistry invites = new Nat20PartyInviteRegistry();
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        reg.getParty(alice);

        assertThrows(IllegalArgumentException.class,
            () -> reg.createInvite(invites, alice, alice));
    }

    @Test
    void createInviteRejectsInviteeAlreadyInMultiMemberParty() {
        Nat20PartyInviteRegistry invites = new Nat20PartyInviteRegistry();
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();

        // bob joins alice's party (now size 2)
        reg.acceptInvite(bob, reg.getParty(alice).getPartyId());

        // carol cannot invite bob because bob is in a multi-member party
        assertThrows(IllegalStateException.class,
            () -> reg.createInvite(invites, carol, bob));
    }

    @Test
    void createInviteOverwritesPriorPendingForSameInvitee() {
        Nat20PartyInviteRegistry invites = new Nat20PartyInviteRegistry();
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String carolPid = reg.getParty(carol).getPartyId();

        reg.createInvite(invites, alice, bob);
        reg.createInvite(invites, carol, bob);

        PartyInvite latest = invites.getForInvitee(bob);
        assertEquals(carol, latest.inviterUuid());
        assertEquals(carolPid, latest.targetPartyId());
    }

    @Test
    void acceptPendingInviteJoinsTargetPartyAndClearsInvite() {
        Nat20PartyInviteRegistry invites = new Nat20PartyInviteRegistry();
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String pid = reg.getParty(alice).getPartyId();

        reg.createInvite(invites, alice, bob);
        reg.acceptPendingInvite(invites, bob);

        assertEquals(java.util.List.of(alice, bob), reg.getParty(alice).getMembers());
        assertNull(invites.getForInvitee(bob), "invite is consumed on accept");
    }

    @Test
    void acceptPendingInviteWithNoPendingThrows() {
        Nat20PartyInviteRegistry invites = new Nat20PartyInviteRegistry();
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID bob = UUID.randomUUID();
        reg.getParty(bob);

        assertThrows(IllegalStateException.class,
            () -> reg.acceptPendingInvite(invites, bob));
    }

    @Test
    void declinePendingInviteClearsTheInviteWithoutJoiningAnyParty() {
        Nat20PartyInviteRegistry invites = new Nat20PartyInviteRegistry();
        Nat20PartyRegistry reg = new Nat20PartyRegistry();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        reg.createInvite(invites, alice, bob);

        reg.declinePendingInvite(invites, bob);

        assertNull(invites.getForInvitee(bob));
        assertTrue(reg.getParty(bob).isSolo(), "decline leaves bob in his own solo party");
        assertEquals(java.util.List.of(alice), reg.getParty(alice).getMembers());
    }
}
