package com.chonbosmods.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-global registry of {@link Nat20Party}. Every player is always in a
 * party: the registry auto-creates a size-1 party the first time a player is
 * looked up.
 *
 * <p>Design reference: {@code docs/plans/2026-04-21-party-multiplayer-quest-design.md}
 * §3 Party system.
 */
public class Nat20PartyRegistry {

    private final Map<UUID, Nat20Party> byPlayer = new HashMap<>();
    private final Map<String, Nat20Party> byPartyId = new HashMap<>();

    public Nat20Party getParty(UUID player) {
        Nat20Party existing = byPlayer.get(player);
        if (existing != null) return existing;
        Nat20Party solo = Nat20Party.ofSolo(player);
        byPlayer.put(player, solo);
        byPartyId.put(solo.getPartyId(), solo);
        return solo;
    }

    public Nat20Party getPartyById(String partyId) {
        return byPartyId.get(partyId);
    }

    public void acceptInvite(UUID invitee, String targetPartyId) {
        Nat20Party target = byPartyId.get(targetPartyId);
        if (target == null) {
            throw new IllegalArgumentException("no such party: " + targetPartyId);
        }
        Nat20Party current = getParty(invitee);
        if (!current.isSolo()) {
            throw new IllegalStateException("player is already in a multi-member party");
        }
        current.removeMember(invitee);
        if (current.isEmpty()) byPartyId.remove(current.getPartyId());
        target.addMember(invitee);
        byPlayer.put(invitee, target);
    }
}
