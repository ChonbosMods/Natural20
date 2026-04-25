package com.chonbosmods.party;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

/**
 * Renders the "Joined Party" event-title banner to existing party members
 * when a new player accepts an invite. Fires per-member (not to the joiner
 * themselves). Mirrors {@link PartyInviteBanner}'s layout and timing.
 */
public final class PartyJoinedBanner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Banner");
    private static final float TITLE_DURATION = 5.0f;
    private static final float TITLE_FADE_IN = 1.0f;
    private static final float TITLE_FADE_OUT = 1.5f;

    private PartyJoinedBanner() {}

    public static void show(PlayerRef member, String joinerName) {
        LOGGER.atInfo().log("Firing party-joined banner: joiner='%s', member=%s",
            joinerName, member.getUuid());
        EventTitleUtil.showEventTitleToPlayer(
            member,
            Message.raw(joinerName + " Has Joined"),
            Message.raw("Your party grows stronger."),
            true,
            null,
            TITLE_DURATION, TITLE_FADE_IN, TITLE_FADE_OUT);
    }
}
