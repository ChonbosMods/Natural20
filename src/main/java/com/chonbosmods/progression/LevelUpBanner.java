package com.chonbosmods.progression;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

/**
 * Renders the "Level Up" event-title banner. Sibling of QuestCompletionBanner:
 * same duration + fade timing, no sound (per design doc G6).
 */
public final class LevelUpBanner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|LvlBanner");
    private static final float TITLE_DURATION = 5.0f;
    private static final float TITLE_FADE_IN = 1.0f;
    private static final float TITLE_FADE_OUT = 1.5f;

    private LevelUpBanner() {}

    public static void show(PlayerRef player, String playerName, int newLevel) {
        LOGGER.atInfo().log("Level-up banner for %s: level %d", playerName, newLevel);
        EventTitleUtil.showEventTitleToPlayer(
                player,
                Message.raw("Level Up"),
                Message.raw(playerName + " reached level " + newLevel),
                true,
                null,
                TITLE_DURATION, TITLE_FADE_IN, TITLE_FADE_OUT);
    }
}
