package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;

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
        // Set flag instead of advancing: player must return to NPC for turn-in
        quest.getVariableBindings().put("phase_objectives_complete", "true");
        LOGGER.atInfo().log("Quest %s phase %d objectives complete: awaiting turn-in",
            quest.getQuestId(), quest.getCurrentPhaseIndex());

        // Refresh markers: swaps POI marker → return marker at settlement
        UUID playerUuid = store.getComponent(playerRef,
            com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
            .getPlayerRef().getUuid();
        QuestMarkerProvider.refreshMarkers(playerUuid, playerData);
    }

    private boolean matchesTarget(ObjectiveInstance obj, String targetId) {
        if (obj.getTargetId() == null || targetId == null) return false;
        return obj.getTargetId().equals(targetId) || targetId.contains(obj.getTargetId());
    }
}
