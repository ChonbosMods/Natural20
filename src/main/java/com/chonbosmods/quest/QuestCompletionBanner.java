package com.chonbosmods.quest;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

/**
 * Renders the "Quest Completed" event-title banner when a quest phase
 * first reaches {@link QuestState#READY_FOR_TURN_IN}. Layout mirrors the
 * settlement discovery banner: large primary text + small secondary text,
 * with the same duration and fade timing.
 */
public final class QuestCompletionBanner {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Banner");
    private static final float TITLE_DURATION = 5.0f;
    private static final float TITLE_FADE_IN = 1.0f;
    private static final float TITLE_FADE_OUT = 1.5f;

    private QuestCompletionBanner() {}

    public static void show(PlayerRef player, QuestInstance quest) {
        String topicHeader = quest.getVariableBindings()
            .getOrDefault("quest_topic_header", "Quest");
        LOGGER.atInfo().log("Firing banner for quest %s: header='%s', player=%s",
            quest.getQuestId(), topicHeader, player.getUuid());
        EventTitleUtil.showEventTitleToPlayer(
            player,
            Message.raw("Quest Completed"),
            Message.raw(topicHeader),
            true,
            null,
            TITLE_DURATION, TITLE_FADE_IN, TITLE_FADE_OUT);
    }
}
