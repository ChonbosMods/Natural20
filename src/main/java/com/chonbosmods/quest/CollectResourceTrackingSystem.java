package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.waypoint.QuestMarkerProvider;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;

import java.util.Map;

/**
 * ECS event system that tracks COLLECT_RESOURCES quest progress by counting
 * matching items across the player's entire hotbar on every inventory change.
 * Unlike FETCH_ITEM (binary pickup), this system maintains a running count
 * and supports both gaining and losing progress (e.g. player drops items).
 */
public class CollectResourceTrackingSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|CollectTrack");
    private static final Query<EntityStore> QUERY = Query.any();

    public CollectResourceTrackingSystem() {
        super(InventoryChangeEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() { return QUERY; }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       InventoryChangeEvent event) {
        Ref<EntityStore> ref = chunk.getReferenceTo(entityIndex);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        Transaction transaction = event.getTransaction();
        if (!transaction.succeeded()) return;

        // Skip armor changes (not relevant for quest items)
        if (event.getComponentType() == InventoryComponent.Armor.getComponentType()) return;

        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        if (playerData == null) return;

        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        if (quests.isEmpty()) return;

        // Count across inventory: try full inventory, fall back to event container
        @SuppressWarnings("unchecked")
        CombinedItemContainer fullInventory = InventoryComponent.getCombined(
            store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        ItemContainer scanContainer = fullInventory != null ? fullInventory : event.getItemContainer();
        boolean dirty = false;

        for (QuestInstance quest : quests.values()) {
            QuestState state = quest.getState();
            if (state != QuestState.ACTIVE_OBJECTIVE && state != QuestState.READY_FOR_TURN_IN) continue;

            ObjectiveInstance obj = quest.getCurrentObjective();
            if (obj == null || obj.getType() != ObjectiveType.COLLECT_RESOURCES) continue;

            String targetItemId = obj.getTargetId();
            if (targetItemId == null) continue;

            // Count ALL matching items in inventory
            int totalCount = 0;
            short capacity = scanContainer.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = scanContainer.getItemStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                if (targetItemId.equals(stack.getItemId())) {
                    totalCount += stack.getQuantity();
                }
            }

            // Update objective progress
            int oldCount = obj.getCurrentCount();
            if (totalCount != oldCount) {
                obj.setCurrentCount(totalCount);
                dirty = true;
                LOGGER.atInfo().log("COLLECT_RESOURCES: quest %s tracking %s: %d -> %d (need %d)",
                    quest.getQuestId(), targetItemId, oldCount, totalCount, obj.getRequiredCount());
            }

            // Check threshold transitions
            boolean wasReady = state == QuestState.READY_FOR_TURN_IN;
            boolean nowComplete = totalCount >= obj.getRequiredCount();

            if (nowComplete && !wasReady) {
                obj.markComplete();
                boolean firstReady = quest.markPhaseReadyForTurnIn();
                dirty = true;
                LOGGER.atInfo().log("COLLECT_RESOURCES: player %s reached %d/%d %s for quest %s",
                    player.getPlayerRef().getUuid(), totalCount, obj.getRequiredCount(),
                    targetItemId, quest.getQuestId());

                // Persist the transition so any downstream read sees committed state.
                questSystem.getStateManager().saveActiveQuests(playerData, quests);
                dirty = false;

                // COLLECT_RESOURCES exemption (2026-04-23): this objective type
                // does NOT fire the party proximity gate. Rationale:
                //   1. Collect objectives can be completed anywhere in the world
                //      by any accepter: a proximity check would be nonsensical
                //      (there is no single place "the objective happened at").
                //   2. InventoryChangeEvent fires on every drop + pickup, so if
                //      we gated here the missed-set + Quest Missed banner would
                //      spam every time the triggering player drops and picks
                //      back up the matching resource.
                // Effect under Option B: missed-set stays empty for this phase,
                // so TURN_IN_V2.dispensePhaseXp and dispenseItemsToOtherAccepters
                // award XP + rerolled items to every online accepter. Offline
                // accepters are still silently skipped by the existing offline
                // filter in the dispense paths. KILL/FETCH/TALK still gate.
                setTurnInParticle(quest);
                if (firstReady) {
                    QuestCompletionBanner.show(player.getPlayerRef(), quest);
                }
                // Refresh waypoints for every online accepter so the phase-ready
                // transition propagates without a /sheet toggle. XP is NOT
                // awarded here: TURN_IN_V2.dispensePhaseXp owns the per-phase
                // grant.
                World world = Natural20.getInstance().getDefaultWorld();
                if (world != null) {
                    Nat20QuestRewardDispatcher.refreshMarkersForAccepters(quest, world);
                }
            } else if (!nowComplete && wasReady) {
                obj.uncomplete();
                quest.setState(QuestState.ACTIVE_OBJECTIVE);
                dirty = true;
                LOGGER.atInfo().log("COLLECT_RESOURCES: player %s dropped to %d/%d %s for quest %s (reverting to active)",
                    player.getPlayerRef().getUuid(), totalCount, obj.getRequiredCount(),
                    targetItemId, quest.getQuestId());
                clearTurnInParticle(quest);
            }
        }

        if (dirty) {
            questSystem.getStateManager().saveActiveQuests(playerData, quests);
            QuestMarkerProvider.refreshMarkers(player.getPlayerRef().getUuid(), playerData);
        }
    }

    private static void setTurnInParticle(QuestInstance quest) {
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null || quest.getSourceSettlementId() == null) return;
        SettlementRecord settlement = settlements.getByCell(quest.getSourceSettlementId());
        if (settlement == null) return;
        NpcRecord npcRecord = settlement.getNpcByName(quest.getSourceNpcId());
        if (npcRecord == null) return;
        npcRecord.setMarkerState("QUEST_TURN_IN");
        settlements.saveAsync();
        if (npcRecord.getEntityUUID() != null) {
            QuestMarkerManager.INSTANCE.syncMarker(
                npcRecord.getEntityUUID(), Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
        }
    }

    private static void clearTurnInParticle(QuestInstance quest) {
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null || quest.getSourceSettlementId() == null) return;
        SettlementRecord settlement = settlements.getByCell(quest.getSourceSettlementId());
        if (settlement == null) return;
        NpcRecord npcRecord = settlement.getNpcByName(quest.getSourceNpcId());
        if (npcRecord == null) return;
        npcRecord.setMarkerState(null);
        settlements.saveAsync();
        if (npcRecord.getEntityUUID() != null) {
            QuestMarkerManager.INSTANCE.evaluateAndApply(npcRecord.getEntityUUID(), npcRecord);
        }
    }
}
