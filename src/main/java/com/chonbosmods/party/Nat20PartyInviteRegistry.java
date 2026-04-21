package com.chonbosmods.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores pending party invites. One entry per invitee UUID; re-invite
 * overwrites prior pending. Persisted alongside the party registry.
 *
 * <p>Design reference: {@code docs/plans/2026-04-21-party-multiplayer-quest-design.md}
 * §3 Invitation, §9 /sheet Invites tab.
 */
public class Nat20PartyInviteRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SAVE_FILE_NAME = "party_invites.json";

    private final Map<UUID, PartyInvite> byInvitee = new HashMap<>();
    private Path saveDirectory;

    /** Bind the world-scoped save directory. Subsequent {@link #save()} /
     *  {@link #load()} calls resolve {@code party_invites.json} inside it. */
    public void setSaveDirectory(Path dir) {
        this.saveDirectory = dir;
    }

    public void save() throws IOException {
        if (saveDirectory == null) {
            throw new IllegalStateException("saveDirectory not set; call setSaveDirectory first");
        }
        saveTo(saveDirectory.resolve(SAVE_FILE_NAME));
    }

    public void load() throws IOException {
        if (saveDirectory == null) {
            throw new IllegalStateException("saveDirectory not set; call setSaveDirectory first");
        }
        loadFrom(saveDirectory.resolve(SAVE_FILE_NAME));
    }

    public void put(PartyInvite invite) {
        byInvitee.put(invite.inviteeUuid(), invite);
    }

    public PartyInvite getForInvitee(UUID invitee) {
        return byInvitee.get(invitee);
    }

    public void removeForInvitee(UUID invitee) {
        byInvitee.remove(invitee);
    }

    public List<PartyInvite> getSentBy(UUID inviter) {
        List<PartyInvite> out = new ArrayList<>();
        for (PartyInvite invite : byInvitee.values()) {
            if (invite.inviterUuid().equals(inviter)) out.add(invite);
        }
        return out;
    }

    public int count() {
        return byInvitee.size();
    }

    public void saveTo(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        List<Record> records = new ArrayList<>(byInvitee.size());
        for (PartyInvite invite : byInvitee.values()) {
            records.add(Record.from(invite));
        }
        Files.writeString(file, GSON.toJson(records));
    }

    public void loadFrom(Path file) throws IOException {
        byInvitee.clear();
        if (!Files.exists(file)) return;
        String json = Files.readString(file);
        if (json.isEmpty()) return;
        Record[] records = GSON.fromJson(json, Record[].class);
        if (records == null) return;
        for (Record r : records) {
            PartyInvite invite = r.toInvite();
            if (invite != null) byInvitee.put(invite.inviteeUuid(), invite);
        }
    }

    /** Plain-object DTO so Gson handles Instant via string round-trip. */
    private static final class Record {
        String inviter;
        String invitee;
        String targetPartyId;
        String createdAt;

        static Record from(PartyInvite invite) {
            Record r = new Record();
            r.inviter = invite.inviterUuid().toString();
            r.invitee = invite.inviteeUuid().toString();
            r.targetPartyId = invite.targetPartyId();
            r.createdAt = invite.createdAt().toString();
            return r;
        }

        PartyInvite toInvite() {
            try {
                return new PartyInvite(
                    UUID.fromString(inviter),
                    UUID.fromString(invitee),
                    targetPartyId,
                    Instant.parse(createdAt)
                );
            } catch (Exception e) {
                return null;
            }
        }
    }
}
