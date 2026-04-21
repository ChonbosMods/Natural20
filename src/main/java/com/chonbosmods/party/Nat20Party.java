package com.chonbosmods.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A server-side party. Every player is always in a party; a solo player
 * inhabits a size-1 party. Members are kept in join order; the leader is the
 * first inviter on an empty team (for the default size-1 party, that's the
 * player themselves).
 *
 * <p>Design reference: {@code docs/plans/2026-04-21-party-multiplayer-quest-design.md}
 * §3 Party system.
 */
public class Nat20Party {

    private final String partyId;
    private final List<UUID> members = new ArrayList<>();
    private UUID leader;

    public Nat20Party(String partyId, UUID leader) {
        this.partyId = partyId;
        this.leader = leader;
        this.members.add(leader);
    }

    public static Nat20Party ofSolo(UUID player) {
        return new Nat20Party(UUID.randomUUID().toString(), player);
    }

    public String getPartyId() { return partyId; }
    public UUID getLeader() { return leader; }
    public List<UUID> getMembers() { return List.copyOf(members); }
    public boolean isSolo() { return members.size() == 1; }
    public boolean isEmpty() { return members.isEmpty(); }

    public void addMember(UUID player) {
        if (!members.contains(player)) members.add(player);
    }
}
