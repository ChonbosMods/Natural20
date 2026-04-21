package com.chonbosmods.party;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

/**
 * Notifies a party member (other than the one who clicked Accept) that a new
 * party quest was accepted on their behalf. The accepting player already sees
 * an inline "Quest accepted: ..." system message, so this banner is only
 * fired for peers. Notification-only; no buttons. Same timing as the other
 * Nat20 banners.
 */
public final class PartyQuestAcceptedBanner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Banner");
    private static final float TITLE_DURATION = 5.0f;
    private static final float TITLE_FADE_IN = 1.0f;
    private static final float TITLE_FADE_OUT = 1.5f;

    private PartyQuestAcceptedBanner() {}

    public static void show(PlayerRef peer, String accepterName, String questLabel) {
        LOGGER.atInfo().log("Firing party-quest-accepted banner: accepter='%s', peer=%s, quest='%s'",
            accepterName, peer.getUuid(), questLabel);
        EventTitleUtil.showEventTitleToPlayer(
            peer,
            Message.raw("Quest Accepted"),
            Message.raw(accepterName + " accepted: " + questLabel),
            true,
            null,
            TITLE_DURATION, TITLE_FADE_IN, TITLE_FADE_OUT);
    }
}
