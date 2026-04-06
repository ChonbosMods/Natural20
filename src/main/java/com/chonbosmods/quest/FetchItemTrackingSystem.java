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
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;

import java.util.Map;

/**
 * ECS event system that detects when a player picks up a FETCH_ITEM quest item.
 * On InventoryChangeEvent, checks if any changed slot contains an item matching
 * an active FETCH_ITEM objective's fetch_item_type binding.
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

        // Only process hotbar/main inventory changes (not armor)
        ComponentType<EntityStore, ?> componentType = event.getComponentType();
        if (componentType != InventoryComponent.Hotbar.getComponentType()) return;

        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        if (playerData == null) return;

        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        ItemContainer changedContainer = event.getItemContainer();

        for (QuestInstance quest : quests.values()) {
            // Skip quests that already have objectives complete (awaiting turn-in)
            if ("true".equals(quest.getVariableBindings().get("phase_objectives_complete"))) continue;

            String fetchItemType = quest.getVariableBindings().get("fetch_item_type");
            if (fetchItemType == null) continue;

            PhaseInstance phase = quest.getCurrentPhase();
            if (phase == null) continue;

            for (ObjectiveInstance obj : phase.getObjectives()) {
                if (obj.getType() != ObjectiveType.FETCH_ITEM || obj.isComplete()) continue;

                // Check if any modified slot now contains the quest item
                short capacity = changedContainer.getCapacity();
                for (short slot = 0; slot < capacity; slot++) {
                    if (!transaction.wasSlotModified(slot)) continue;

                    ItemStack stack = changedContainer.getItemStack(slot);
                    if (stack == null || stack.isEmpty()) continue;

                    if (fetchItemType.equals(stack.getItemId())) {
                        // Quest item found in inventory
                        obj.markComplete();
                        quest.getVariableBindings().put("phase_objectives_complete", "true");

                        // Save and refresh markers
                        quests.put(quest.getQuestId(), quest);
                        questSystem.getStateManager().saveActiveQuests(playerData, quests);
                        QuestMarkerProvider.refreshMarkers(
                            player.getPlayerRef().getUuid(), playerData);

                        // Set QUEST_TURN_IN particle on source NPC
                        setTurnInParticle(quest);

                        LOGGER.atInfo().log("FETCH_ITEM: player %s picked up %s for quest %s",
                            player.getPlayerRef().getUuid(), fetchItemType, quest.getQuestId());
                        return; // Done: one pickup per event
                    }
                }
            }
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
}
