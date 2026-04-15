package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;

/**
 * ECS event system that detects when a player picks up or drops a FETCH_ITEM quest item.
 * Scans full inventory (hotbar + backpack + utility) on every change.
 * Reverts to ACTIVE_OBJECTIVE if the item is no longer in inventory.
 */
public class FetchItemTrackingSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|FetchTrack");
    private static final Query<EntityStore> QUERY = Query.any();

    public FetchItemTrackingSystem() {
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

        // Skip armor changes
        if (event.getComponentType() == InventoryComponent.Armor.getComponentType()) return;

        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        if (playerData == null) return;

        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        boolean dirty = false;

        // Scan inventory: try full, fall back to event container
        @SuppressWarnings("unchecked")
        CombinedItemContainer fullInventory = InventoryComponent.getCombined(
            store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        ItemContainer scanContainer = fullInventory != null ? fullInventory : event.getItemContainer();

        for (QuestInstance quest : quests.values()) {
            QuestState state = quest.getState();
            if (state != QuestState.ACTIVE_OBJECTIVE && state != QuestState.READY_FOR_TURN_IN) continue;

            String fetchItemType = quest.getVariableBindings().get("fetch_item_type");
            if (fetchItemType == null) continue;

            ObjectiveInstance obj = quest.getCurrentObjective();
            if (obj == null || (obj.getType() != ObjectiveType.FETCH_ITEM
                    && obj.getType() != ObjectiveType.PEACEFUL_FETCH)) continue;

            // Check if fetch item exists anywhere in inventory
            boolean hasItem = false;
            short capacity = scanContainer.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = scanContainer.getItemStack(slot);
                if (stack != null && !stack.isEmpty() && fetchItemType.equals(stack.getItemId())) {
                    hasItem = true;
                    break;
                }
            }

            boolean wasReady = state == QuestState.READY_FOR_TURN_IN;

            if (hasItem && !wasReady) {
                // Picked up quest item
                obj.markComplete();
                if (quest.markPhaseReadyForTurnIn()) {
                    QuestCompletionBanner.show(player.getPlayerRef(), quest);
                    int xp = com.chonbosmods.progression.Nat20XpMath.questPhaseXp(playerData.getLevel());
                    Natural20.getInstance().getXpService().award(player, ref, store, xp,
                            "quest:" + quest.getQuestId());
                }
                dirty = true;
                setTurnInParticle(quest);
                LOGGER.atInfo().log("FETCH_ITEM: player %s picked up %s for quest %s",
                    player.getPlayerRef().getUuid(), fetchItemType, quest.getQuestId());
            } else if (!hasItem && wasReady) {
                // Dropped quest item: revert to active
                obj.uncomplete();
                quest.setState(QuestState.ACTIVE_OBJECTIVE);
                dirty = true;
                clearTurnInParticle(quest);
                LOGGER.atInfo().log("FETCH_ITEM: player %s lost %s for quest %s (reverting to active)",
                    player.getPlayerRef().getUuid(), fetchItemType, quest.getQuestId());
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
