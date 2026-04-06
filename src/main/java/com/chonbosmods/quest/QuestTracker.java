package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
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
            if (quest.getState() != QuestState.ACTIVE_OBJECTIVE) continue;

            ObjectiveInstance obj = quest.getCurrentObjective();
            if (obj == null || obj.isComplete()) continue;
            if (obj.getType() != type) continue;
            if (!matchesTarget(obj, targetId)) continue;

            obj.incrementProgress(amount);
            changed = true;

            LOGGER.atInfo().log("Quest %s: objective %s progress %d/%d",
                quest.getQuestId(), obj.getType(), obj.getCurrentCount(), obj.getRequiredCount());

            if (obj.isComplete()) {
                onObjectiveComplete(playerRef, store, playerData, quest);
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

    private void onObjectiveComplete(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                  Nat20PlayerData playerData, QuestInstance quest) {
        quest.setState(QuestState.READY_FOR_TURN_IN);
        LOGGER.atInfo().log("Quest %s objective complete (conflict %d): awaiting turn-in",
            quest.getQuestId(), quest.getConflictCount());

        UUID playerUuid = store.getComponent(playerRef,
            com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
            .getPlayerRef().getUuid();
        QuestMarkerProvider.refreshMarkers(playerUuid, playerData);

        setTurnInParticle(quest, store);
    }

    /**
     * Set QUEST_TURN_IN particle on the quest's source NPC.
     */
    private void setTurnInParticle(QuestInstance quest, Store<EntityStore> store) {
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null || quest.getSourceSettlementId() == null) return;

        SettlementRecord settlement = settlements.getByCell(quest.getSourceSettlementId());
        if (settlement == null) return;

        NpcRecord npcRecord = settlement.getNpcByName(quest.getSourceNpcId());
        if (npcRecord == null) return;

        // Persist to NpcRecord so it survives entity UUID changes on chunk reload
        npcRecord.setMarkerState("QUEST_TURN_IN");
        settlements.saveAsync();

        if (npcRecord.getEntityUUID() != null) {
            QuestMarkerManager.INSTANCE.syncMarker(
                npcRecord.getEntityUUID(), Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
        }
    }

    private boolean matchesTarget(ObjectiveInstance obj, String targetId) {
        if (obj.getTargetId() == null || targetId == null) return false;
        return obj.getTargetId().equals(targetId) || targetId.contains(obj.getTargetId());
    }
}
