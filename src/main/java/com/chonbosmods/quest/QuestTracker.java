package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;

public class QuestTracker {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final QuestStateManager stateManager;
    private final QuestRewardManager rewardManager;

    public QuestTracker(QuestStateManager stateManager, QuestRewardManager rewardManager) {
        this.stateManager = stateManager;
        this.rewardManager = rewardManager;
    }

    public void reportProgress(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                ObjectiveType type, String targetId, int amount) {
        Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (playerData == null) return;

        Map<String, QuestInstance> quests = stateManager.getActiveQuests(playerData);
        boolean changed = false;

        for (QuestInstance quest : quests.values()) {
            PhaseInstance phase = quest.getCurrentPhase();
            if (phase == null) continue;

            for (ObjectiveInstance obj : phase.getObjectives()) {
                if (obj.isComplete()) continue;
                if (obj.getType() != type) continue;
                if (!matchesTarget(obj, targetId)) continue;

                obj.incrementProgress(amount);
                changed = true;

                LOGGER.atInfo().log("Quest %s: objective %s progress %d/%d",
                    quest.getQuestId(), obj.getType(), obj.getCurrentCount(), obj.getRequiredCount());
            }

            if (phase.isComplete()) {
                onPhaseComplete(playerRef, store, playerData, quest);
            }
        }

        if (changed) {
            stateManager.saveActiveQuests(playerData, quests);
        }
    }

    public void reportCompletion(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                  ObjectiveType type, String targetId) {
        reportProgress(playerRef, store, type, targetId, 1);
    }

    private void onPhaseComplete(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                  Nat20PlayerData playerData, QuestInstance quest) {
        PhaseInstance completedPhase = quest.getCurrentPhase();
        boolean isFinalPhase = quest.getCurrentPhaseIndex() >= quest.getPhases().size() - 1;

        rewardManager.awardPhaseXP(playerData, completedPhase, isFinalPhase, quest.getPhases().size());

        if (completedPhase.getType() == PhaseType.RESOLUTION) {
            if (isFinalPhase || rewardManager.shouldGiveMidChainReward(quest)) {
                rewardManager.awardLootReward(playerRef, store, playerData);
                quest.claimReward(quest.getCurrentPhaseIndex());
            }
        }

        if (isFinalPhase) {
            LOGGER.atInfo().log("Quest %s completed", quest.getQuestId());
            stateManager.markQuestCompleted(playerData, quest.getQuestId());
        } else {
            quest.advancePhase();
            LOGGER.atInfo().log("Quest %s advanced to phase %d: %s",
                quest.getQuestId(), quest.getCurrentPhaseIndex(), quest.getCurrentPhase().getType());
        }
    }

    private boolean matchesTarget(ObjectiveInstance obj, String targetId) {
        if (obj.getTargetId() == null || targetId == null) return false;
        return obj.getTargetId().equals(targetId) || targetId.contains(obj.getTargetId());
    }
}
