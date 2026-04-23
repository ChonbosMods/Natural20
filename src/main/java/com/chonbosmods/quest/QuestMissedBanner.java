package com.chonbosmods.quest;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

/**
 * Renders the "Quest Missed" event-title banner when a quest's phase-deadline
 * elapses without completion. Layout mirrors {@link QuestCompletionBanner}:
 * large primary text + small secondary text, with the same duration and fade
 * timing. Supports deferred delivery via a denormalized topic-header string
 * for the offline-turn-in ghost case.
 */
public final class QuestMissedBanner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Banner");
    private static final float TITLE_DURATION = 5.0f;
    private static final float TITLE_FADE_IN = 1.0f;
    private static final float TITLE_FADE_OUT = 1.5f;

    private QuestMissedBanner() {}

    public static void show(PlayerRef player, String topicHeader) {
        LOGGER.atInfo().log("Firing Quest Missed banner: header='%s', player=%s",
            topicHeader, player.getUuid());
        EventTitleUtil.showEventTitleToPlayer(
            player,
            Message.raw("Quest Missed"),
            Message.raw(topicHeader),
            true,
            null,
            TITLE_DURATION, TITLE_FADE_IN, TITLE_FADE_OUT);
    }

    public static void show(PlayerRef player, QuestInstance quest) {
        String topicHeader = quest.getVariableBindings()
            .getOrDefault("quest_topic_header", "Quest");
        show(player, topicHeader);
    }
}
