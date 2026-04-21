package com.chonbosmods.party;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

/**
 * Renders the "Party Invite" event-title banner to an online invitee. Follows
 * the same layout and timing as {@code QuestCompletionBanner} and the
 * settlement discovery banner: large primary text + small secondary text.
 *
 * <p>This banner is notification-only. Accept / decline happens in the /sheet
 * Invites tab. If the invitee is offline at invite time, no banner fires; the
 * invite sits in {@link Nat20PartyInviteRegistry} and surfaces on next login
 * when they open /sheet.
 */
public final class PartyInviteBanner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Banner");
    private static final float TITLE_DURATION = 5.0f;
    private static final float TITLE_FADE_IN = 1.0f;
    private static final float TITLE_FADE_OUT = 1.5f;

    private PartyInviteBanner() {}

    public static void show(PlayerRef invitee, String inviterName) {
        LOGGER.atInfo().log("Firing party-invite banner: inviter='%s', invitee=%s",
            inviterName, invitee.getUuid());
        EventTitleUtil.showEventTitleToPlayer(
            invitee,
            Message.raw("Party Invite"),
            Message.raw(inviterName + " invited you. Open /sheet to respond."),
            true,
            null,
            TITLE_DURATION, TITLE_FADE_IN, TITLE_FADE_OUT);
    }
}
