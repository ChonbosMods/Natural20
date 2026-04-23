package com.chonbosmods.dialogue;

import com.chonbosmods.Natural20;
import com.chonbosmods.dialogue.model.DialogueCondition;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestState;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

public final class ConditionEvaluator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Map<String, BiPredicate<Map<String, String>, ConditionContext>> handlers = new HashMap<>();

    public ConditionEvaluator() {
        register("HAS_FLAG", (params, ctx) -> {
            String flagId = params.get("flagId");
            String expected = params.getOrDefault("value", "true");
            String actual = ctx.playerData().getGlobalFlags().get(flagId);
            return expected.equals(actual);
        });

        register("HAS_ITEM", (params, ctx) -> {
            // Stub: item inventory check requires SDK integration
            return true;
        });

        register("STAT_CHECK", (params, ctx) -> {
            String statName = params.get("stat");
            int minValue = Integer.parseInt(params.getOrDefault("minValue", "0"));
            Stat stat = Stat.valueOf(statName);
            int playerStat = ctx.playerData().getStats()[stat.index()];
            return playerStat >= minValue;
        });

        register("DISPOSITION_MIN", (params, ctx) -> {
            int minDisposition = Integer.parseInt(params.getOrDefault("minDisposition", "0"));
            return ctx.disposition() >= minDisposition;
        });

        register("TOPIC_EXHAUSTED", (params, ctx) -> {
            String topicId = params.get("topicId");
            return ctx.exhaustedTopics().containsKey(topicId);
        });

        register("TOPIC_LEARNED", (params, ctx) -> {
            String topicId = params.get("topicId");
            return ctx.learnedGlobalTopics().contains(topicId);
        });

        register("QUEST_PHASE_STATE", (params, ctx) -> {
            String questId = params.get("questId");
            if (questId == null) return false;
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null) return false;
            QuestInstance quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
            if (quest == null) return false;
            String phaseRaw = params.get("phase");
            if (phaseRaw != null) {
                try {
                    if (quest.getConflictCount() != Integer.parseInt(phaseRaw)) return false;
                } catch (NumberFormatException e) { return false; }
            }
            String stateRaw = params.get("state");
            if (stateRaw != null) {
                try {
                    if (quest.getState() != QuestState.valueOf(stateRaw)) return false;
                } catch (IllegalArgumentException e) { return false; }
            }
            return true;
        });
    }

    public void register(String type, BiPredicate<Map<String, String>, ConditionContext> handler) {
        handlers.put(type, handler);
    }

    public boolean evaluate(DialogueCondition condition, ConditionContext context) {
        if (condition == null) return true;

        if (condition.all() != null) {
            return condition.all().stream().allMatch(c -> evaluate(c, context));
        }
        if (condition.any() != null) {
            return condition.any().stream().anyMatch(c -> evaluate(c, context));
        }

        String type = condition.type();
        if (type == null) {
            LOGGER.atWarning().log("Condition has no type and is not composite");
            return false;
        }

        var handler = handlers.get(type);
        if (handler == null) {
            LOGGER.atWarning().log("Unknown condition type: %s", type);
            return false;
        }

        Map<String, String> params = condition.params() != null ? condition.params() : Map.of();
        return handler.test(params, context);
    }
}
