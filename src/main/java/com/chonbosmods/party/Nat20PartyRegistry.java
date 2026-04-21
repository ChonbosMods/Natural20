package com.chonbosmods.party;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-global registry of {@link Nat20Party}. Every player is always in a
 * party: the registry auto-creates a size-1 party the first time a player is
 * looked up.
 *
 * <p>Design reference: {@code docs/plans/2026-04-21-party-multiplayer-quest-design.md}
 * §3 Party system.
 */
public class Nat20PartyRegistry {

    private static final Duration DEFAULT_GHOST_THRESHOLD = Duration.ofDays(7);

    private final Map<UUID, Nat20Party> byPlayer = new HashMap<>();
    private final Map<String, Nat20Party> byPartyId = new HashMap<>();
    private final Map<UUID, Instant> lastSeen = new HashMap<>();
    private final Set<UUID> online = new HashSet<>();

    private final Supplier<Instant> clock;
    private final Duration ghostThreshold;

    public Nat20PartyRegistry() {
        this(Instant::now, DEFAULT_GHOST_THRESHOLD);
    }

    public Nat20PartyRegistry(Supplier<Instant> clock, Duration ghostThreshold) {
        this.clock = clock;
        this.ghostThreshold = ghostThreshold;
    }

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

    public void kick(UUID kicker, UUID target) {
        if (kicker.equals(target)) {
            throw new IllegalArgumentException("cannot kick self; use leave() instead");
        }
        Nat20Party party = byPlayer.get(kicker);
        if (party == null || !kicker.equals(party.getLeader())) {
            throw new SecurityException("only the party leader can kick members");
        }
        if (!party.getMembers().contains(target)) {
            throw new IllegalArgumentException("target is not a member of the kicker's party");
        }
        party.removeMember(target);
        Nat20Party targetSolo = Nat20Party.ofSolo(target);
        byPlayer.put(target, targetSolo);
        byPartyId.put(targetSolo.getPartyId(), targetSolo);
    }

    public void leave(UUID player) {
        Nat20Party current = byPlayer.get(player);
        if (current != null) {
            current.removeMember(player);
            if (current.isEmpty()) byPartyId.remove(current.getPartyId());
        }
        Nat20Party solo = Nat20Party.ofSolo(player);
        byPlayer.put(player, solo);
        byPartyId.put(solo.getPartyId(), solo);
    }

    public void markOnline(UUID player) {
        online.add(player);
        Instant t = clock.get();
        lastSeen.put(player, t);
        applyGhostLeaderRule(player, t);
    }

    public void markOffline(UUID player) {
        online.remove(player);
        lastSeen.put(player, clock.get());
    }

    /**
     * If {@code player} is a non-leader in a multi-member party and the
     * party's current leader has been offline longer than {@link #ghostThreshold},
     * promote {@code player} to leader. This fires on login only, and the rule
     * is a no-op when the leader is still online or when the party is size 1.
     */
    private void applyGhostLeaderRule(UUID player, Instant now) {
        Nat20Party party = byPlayer.get(player);
        if (party == null || party.isSolo()) return;
        UUID currentLeader = party.getLeader();
        if (player.equals(currentLeader)) return;
        if (online.contains(currentLeader)) return;
        Instant leaderSeen = lastSeen.getOrDefault(currentLeader, Instant.EPOCH);
        if (Duration.between(leaderSeen, now).compareTo(ghostThreshold) > 0) {
            party.promoteToLeader(player);
        }
    }
}
