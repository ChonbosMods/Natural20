package com.chonbosmods.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

    public boolean isOnline(UUID player) {
        return online.contains(player);
    }

    /**
     * Create a pending invite from {@code inviter} to {@code invitee} targeting
     * the inviter's current party. Rejects self-invites and invites to players
     * already in a multi-member party; replaces any prior pending invite for
     * the same invitee. Does not fire any banner; the caller (plugin) is
     * responsible for visual notification.
     */
    public void createInvite(Nat20PartyInviteRegistry invites, UUID inviter, UUID invitee) {
        if (inviter.equals(invitee)) {
            throw new IllegalArgumentException("cannot invite self");
        }
        Nat20Party inviteeCurrent = getParty(invitee);
        if (!inviteeCurrent.isSolo()) {
            throw new IllegalStateException("invitee is already in a multi-member party");
        }
        Nat20Party inviterParty = getParty(inviter);
        invites.put(new PartyInvite(inviter, invitee, inviterParty.getPartyId(), clock.get()));
    }

    /**
     * Accept the pending invite for {@code invitee}. The invitee joins the
     * party identified by the invite's {@code targetPartyId} and the invite is
     * removed. Throws if no pending invite exists.
     */
    public void acceptPendingInvite(Nat20PartyInviteRegistry invites, UUID invitee) {
        PartyInvite pending = invites.getForInvitee(invitee);
        if (pending == null) {
            throw new IllegalStateException("no pending invite for " + invitee);
        }
        acceptInvite(invitee, pending.targetPartyId());
        invites.removeForInvitee(invitee);
    }

    /** Decline (discard) the pending invite for {@code invitee}. No-op if none. */
    public void declinePendingInvite(Nat20PartyInviteRegistry invites, UUID invitee) {
        invites.removeForInvitee(invitee);
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

    public void saveTo(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Snapshot snap = new Snapshot();
        for (Nat20Party party : byPartyId.values()) {
            PartySnapshot ps = new PartySnapshot();
            ps.leader = party.getLeader();
            ps.members = new ArrayList<>(party.getMembers());
            snap.parties.put(party.getPartyId(), ps);
        }
        for (Map.Entry<UUID, Instant> e : lastSeen.entrySet()) {
            snap.lastSeen.put(e.getKey().toString(), e.getValue().toString());
        }
        Files.writeString(file, GSON.toJson(snap));
    }

    public void loadFrom(Path file) throws IOException {
        byPlayer.clear();
        byPartyId.clear();
        lastSeen.clear();
        online.clear();
        if (!Files.exists(file)) return;
        String json = Files.readString(file);
        if (json.isEmpty()) return;
        Snapshot snap = GSON.fromJson(json, Snapshot.class);
        if (snap == null) return;
        if (snap.parties != null) {
            for (Map.Entry<String, PartySnapshot> e : snap.parties.entrySet()) {
                PartySnapshot ps = e.getValue();
                if (ps == null || ps.members == null || ps.members.isEmpty()) continue;
                Nat20Party party = new Nat20Party(e.getKey(), ps.members.get(0));
                for (int i = 1; i < ps.members.size(); i++) {
                    party.addMember(ps.members.get(i));
                }
                if (ps.leader != null && ps.members.contains(ps.leader)) {
                    party.promoteToLeader(ps.leader);
                }
                byPartyId.put(party.getPartyId(), party);
                for (UUID m : ps.members) byPlayer.put(m, party);
            }
        }
        if (snap.lastSeen != null) {
            for (Map.Entry<String, String> e : snap.lastSeen.entrySet()) {
                lastSeen.put(UUID.fromString(e.getKey()), Instant.parse(e.getValue()));
            }
        }
    }

    private static class Snapshot {
        Map<String, PartySnapshot> parties = new HashMap<>();
        Map<String, String> lastSeen = new HashMap<>();
    }

    private static class PartySnapshot {
        UUID leader;
        List<UUID> members;
    }
}
