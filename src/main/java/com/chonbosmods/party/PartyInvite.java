package com.chonbosmods.party;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending party invite: the inviter sent it, the invitee can accept or
 * decline, and it targets a specific party (captured at invite time so
 * acceptance still works if the inviter's current party has grown or churned).
 *
 * <p>Design reference: {@code docs/plans/2026-04-21-party-multiplayer-quest-design.md}
 * §3 Party system / Invitation.
 */
public record PartyInvite(
        UUID inviterUuid,
        UUID inviteeUuid,
        String targetPartyId,
        Instant createdAt
) {}
