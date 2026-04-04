package com.chonbosmods.action;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.ObjectiveType;
import com.chonbosmods.quest.PhaseInstance;
import com.chonbosmods.quest.POIPopulationListener;
import com.chonbosmods.quest.QuestStateManager;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.component.Ref;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.chonbosmods.waypoint.QuestMarkerProvider;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class DialogueActionRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    // Action type constants: use these instead of raw strings when building
    // dialogue nodes or checking action types programmatically.
    public static final String SET_FLAG = "SET_FLAG";
    public static final String MODIFY_DISPOSITION = "MODIFY_DISPOSITION";
    public static final String GIVE_ITEM = "GIVE_ITEM";
    public static final String REMOVE_ITEM = "REMOVE_ITEM";
    public static final String UNLOCK_TOPIC = "UNLOCK_TOPIC";
    public static final String EXECUTE_COMMAND = "EXECUTE_COMMAND";
    public static final String GIVE_QUEST = "GIVE_QUEST";
    public static final String COMPLETE_QUEST = "COMPLETE_QUEST";
    public static final String TURN_IN_PHASE = "TURN_IN_PHASE";
    public static final String COMPLETE_TALK_TO_NPC = "COMPLETE_TALK_TO_NPC";
    public static final String OPEN_SHOP = "OPEN_SHOP";
    public static final String CHANGE_REPUTATION = "CHANGE_REPUTATION";
    public static final String EXHAUST_TOPIC = "EXHAUST_TOPIC";
    public static final String REACTIVATE_TOPIC = "REACTIVATE_TOPIC";

    private final Map<String, DialogueAction> actions = new HashMap<>();

    public DialogueActionRegistry() {
        register(SET_FLAG, (ctx, params) -> {
            String flagId = params.get("flagId");
            String value = params.getOrDefault("value", "true");
            ctx.playerData().getGlobalFlags().put(flagId, value);
        });

        register(MODIFY_DISPOSITION, (ctx, params) -> {
            int amount = Integer.parseInt(params.getOrDefault("amount", "0"));
            ctx.dispositionUpdater().accept(amount);
        });

        register(GIVE_ITEM, (ctx, params) -> {
            String itemId = params.get("itemId");
            int quantity = Integer.parseInt(params.getOrDefault("quantity", "1"));
            LOGGER.atInfo().log("GIVE_ITEM stub: %s x%d to %s", itemId, quantity, ctx.player().getPlayerRef().getUuid());
        });

        register(REMOVE_ITEM, (ctx, params) -> {
            String itemId = params.get("itemId");
            int quantity = Integer.parseInt(params.getOrDefault("quantity", "1"));
            LOGGER.atInfo().log("REMOVE_ITEM stub: %s x%d from %s", itemId, quantity, ctx.player().getPlayerRef().getUuid());
        });

        register(UNLOCK_TOPIC, (ctx, params) -> {
            String topicId = params.get("topicId");
            String scope = params.getOrDefault("scope", "LOCAL");
            if ("GLOBAL".equals(scope)) {
                ctx.globalTopicUnlocker().accept(topicId);
                LOGGER.atInfo().log("UNLOCK_TOPIC: player %s learned global topic '%s' (via NPC %s)",
                    ctx.player().getPlayerRef().getUuid(), topicId, ctx.npcId());
            }
        });

        register(EXECUTE_COMMAND, (ctx, params) -> {
            String command = params.getOrDefault("command", "");
            LOGGER.atInfo().log("EXECUTE_COMMAND stub: %s", command);
        });

        register(GIVE_QUEST, (ctx, params) -> {
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null) {
                LOGGER.atWarning().log("GIVE_QUEST: quest system not initialized");
                return;
            }

            // Look up pre-generated quest from NPC's settlement record
            String npcName = ctx.npcId();
            String cellKey = ctx.npcData() != null ? ctx.npcData().getSettlementCellKey() : "";
            SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
            if (settlements == null || cellKey.isEmpty()) {
                LOGGER.atWarning().log("GIVE_QUEST: no settlement registry or cell key for NPC %s", npcName);
                return;
            }

            SettlementRecord settlement = settlements.getByCell(cellKey);
            if (settlement == null) {
                LOGGER.atWarning().log("GIVE_QUEST: settlement not found for cell %s", cellKey);
                return;
            }

            NpcRecord npcRecord = settlement.getNpcByName(npcName);
            if (npcRecord == null || npcRecord.getPreGeneratedQuest() == null) {
                LOGGER.atWarning().log("GIVE_QUEST: no pre-generated quest for NPC %s", npcName);
                return;
            }

            QuestInstance quest = npcRecord.getPreGeneratedQuest();

            // Check if player already has this quest or completed it
            Set<String> completedIds = questSystem.getStateManager().getCompletedQuestIds(ctx.playerData());
            if (completedIds.contains(quest.getQuestId())) {
                LOGGER.atInfo().log("GIVE_QUEST: player already completed quest %s", quest.getQuestId());
                return;
            }
            if (questSystem.getStateManager().getQuest(ctx.playerData(), quest.getQuestId()) != null) {
                LOGGER.atInfo().log("GIVE_QUEST: player already has quest %s", quest.getQuestId());
                return;
            }

            // Add quest to player's active quests (marker offset computed after POI placement)
            questSystem.getStateManager().addQuest(ctx.playerData(), quest);

            // Trigger POI placement if applicable
            if ("true".equals(quest.getVariableBindings().get("poi_available"))) {
                String popSpec = quest.getVariableBindings().get("poi_population_spec");
                String mobRole = null;
                int mobCount = 0;
                if (popSpec != null && !popSpec.equals("NONE")) {
                    int firstColon = popSpec.indexOf(':');
                    int lastColon = popSpec.lastIndexOf(':');
                    if (firstColon > 0 && lastColon > firstColon) {
                        mobRole = popSpec.substring(firstColon + 1, lastColon);
                        mobCount = Integer.parseInt(popSpec.substring(lastColon + 1));
                    }
                }
                triggerPOIPlacement(quest, ctx.store(), ctx.playerRef(), mobRole, mobCount);
            }

            String questLabel = quest.getVariableBindings().getOrDefault("quest_objective_summary",
                quest.getSituationId());
            ctx.systemLogger().accept("Quest accepted: " + questLabel);
            LOGGER.atInfo().log("GIVE_QUEST: player %s received quest '%s' from NPC %s",
                ctx.player().getPlayerRef().getUuid(), quest.getQuestId(), npcName);

            // Inject references
            double npcX = 0, npcZ = 0;
            try {
                var transform = ctx.store().getComponent(ctx.npcRef(),
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
                if (transform != null) {
                    var pos = transform.getPosition();
                    npcX = pos.getX();
                    npcZ = pos.getZ();
                }
            } catch (Exception ignored) {}

            for (var phase : quest.getPhases()) {
                if (phase.getReferenceId() != null) {
                    questSystem.getReferenceManager().injectReference(
                        ctx.playerData(), quest.getSituationId(),
                        phase.getReferenceId(), npcX, npcZ);
                }
            }

            // Consume the pre-generated quest so it can't be given again
            npcRecord.setPreGeneratedQuest(null);
            settlements.saveAsync();

            // Update waypoint marker cache
            QuestMarkerProvider.refreshMarkers(
                ctx.player().getPlayerRef().getUuid(), ctx.playerData());
        });

        register(COMPLETE_QUEST, (ctx, params) -> {
            String questId = params.get("questId");
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null || questId == null) return;

            QuestInstance quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
            if (quest != null && quest.isComplete()) {
                questSystem.getStateManager().markQuestCompleted(ctx.playerData(), questId);
                questSystem.getReferenceManager().cleanupQuestReferences(ctx.playerData(), quest);
                ctx.player().sendMessage(Message.raw("Quest completed: " + quest.getSituationId()));

                // Update waypoint marker cache
                QuestMarkerProvider.refreshMarkers(
                        ctx.player().getPlayerRef().getUuid(), ctx.playerData());
            }
        });

        register(TURN_IN_PHASE, (ctx, params) -> {
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null) return;

            // Find the quest this NPC gave that has completed objectives
            String questId = params.get("questId");
            QuestInstance quest;
            if (questId != null) {
                quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
            } else {
                // Auto-detect: find quest from this NPC with phase_objectives_complete
                quest = null;
                for (QuestInstance q : questSystem.getStateManager().getActiveQuests(ctx.playerData()).values()) {
                    if (ctx.npcId().equals(q.getSourceNpcId())
                            && "true".equals(q.getVariableBindings().get("phase_objectives_complete"))) {
                        quest = q;
                        break;
                    }
                }
            }

            if (quest == null || !"true".equals(quest.getVariableBindings().get("phase_objectives_complete"))) {
                LOGGER.atWarning().log("TURN_IN_PHASE: no quest ready for turn-in from NPC %s", ctx.npcId());
                return;
            }

            PhaseInstance completedPhase = quest.getCurrentPhase();
            boolean isFinalPhase = quest.getCurrentPhaseIndex() >= quest.getPhases().size() - 1;

            // Award phase rewards
            questSystem.getRewardManager().awardPhaseXP(
                ctx.playerData(), completedPhase, isFinalPhase, quest.getPhases().size());

            if (completedPhase.getType() == com.chonbosmods.quest.PhaseType.RESOLUTION) {
                if (isFinalPhase || questSystem.getRewardManager().shouldGiveMidChainReward(quest)) {
                    questSystem.getRewardManager().awardLootReward(ctx.playerRef(), ctx.store(), ctx.playerData());
                    quest.claimReward(quest.getCurrentPhaseIndex());
                }
            }

            // Consume quest item for FETCH_ITEM objectives
            for (ObjectiveInstance obj : completedPhase.getObjectives()) {
                if (obj.getType() == com.chonbosmods.quest.ObjectiveType.FETCH_ITEM) {
                    String fetchItemType = quest.getVariableBindings().get("fetch_item_type");
                    if (fetchItemType != null) {
                        boolean consumed = consumeFetchItem(ctx, fetchItemType);
                        if (consumed) {
                            LOGGER.atInfo().log("TURN_IN_PHASE: consumed quest item %s for quest %s",
                                fetchItemType, quest.getQuestId());
                        } else {
                            LOGGER.atWarning().log("TURN_IN_PHASE: quest item %s not found in inventory for quest %s (allowing turn-in anyway)",
                                fetchItemType, quest.getQuestId());
                        }
                    }
                }
            }

            // Consume resources for COLLECT_RESOURCES objectives
            for (ObjectiveInstance obj : completedPhase.getObjectives()) {
                if (obj.getType() == ObjectiveType.COLLECT_RESOURCES) {
                    String resourceId = obj.getTargetId();
                    int requiredCount = obj.getRequiredCount();
                    if (resourceId != null) {
                        int consumed = consumeResources(ctx, resourceId, requiredCount);
                        if (consumed >= requiredCount) {
                            LOGGER.atInfo().log("TURN_IN_PHASE: consumed %d/%d %s for quest %s",
                                consumed, requiredCount, resourceId, quest.getQuestId());
                        } else {
                            LOGGER.atWarning().log("TURN_IN_PHASE: only consumed %d/%d %s for quest %s (allowing turn-in)",
                                consumed, requiredCount, resourceId, quest.getQuestId());
                        }
                    }
                }
            }

            // Clear flag
            quest.getVariableBindings().remove("phase_objectives_complete");

            if (isFinalPhase) {
                LOGGER.atInfo().log("TURN_IN_PHASE: quest %s completed via turn-in", quest.getQuestId());
                questSystem.getStateManager().markQuestCompleted(ctx.playerData(), quest.getQuestId());
                questSystem.getReferenceManager().cleanupQuestReferences(ctx.playerData(), quest);
                ctx.systemLogger().accept("Quest completed: " + quest.getSituationId());
            } else {
                quest.advancePhase();

                // Reset POI mob state for the new phase so proximity system spawns fresh mobs
                if ("true".equals(quest.getVariableBindings().get("poi_available"))) {
                    quest.getVariableBindings().put("poi_mob_state", "PENDING");
                    quest.getVariableBindings().put("poi_mob_uuids", "");
                    quest.getVariableBindings().remove("poi_detached_uuids");
                }

                // Build objective summary for the new phase
                PhaseInstance newPhase = quest.getCurrentPhase();
                if (newPhase != null && !newPhase.getObjectives().isEmpty()) {
                    ObjectiveInstance obj = newPhase.getObjectives().getFirst();
                    String summary = switch (obj.getType()) {
                        case KILL_MOBS -> "kill " + obj.getRequiredCount() + " " + obj.getEffectiveLabel();
                        case COLLECT_RESOURCES -> "collect " + obj.getRequiredCount() + " " + obj.getEffectiveLabel();
                        case FETCH_ITEM -> "hostile".equals(quest.getVariableBindings().get("fetch_variant"))
                            ? "retrieve " + obj.getTargetLabel() + " from " + quest.getVariableBindings().getOrDefault("subject_name", "the area")
                            : "recover " + obj.getTargetLabel();
                        case TALK_TO_NPC -> "speak with " + obj.getTargetLabel();
                    };
                    quest.getVariableBindings().put("quest_objective_summary", summary);
                }

                // Save with the modified quest re-inserted (getActiveQuests deserializes fresh)
                Map<String, QuestInstance> allQuests = questSystem.getStateManager().getActiveQuests(ctx.playerData());
                allQuests.put(quest.getQuestId(), quest);
                questSystem.getStateManager().saveActiveQuests(ctx.playerData(), allQuests);

                LOGGER.atInfo().log("TURN_IN_PHASE: quest %s advanced to phase %d: %s",
                    quest.getQuestId(), quest.getCurrentPhaseIndex(), quest.getCurrentPhase().getType());
            }

            // Refresh markers
            QuestMarkerProvider.refreshMarkers(
                ctx.player().getPlayerRef().getUuid(), ctx.playerData());
        });

        register(COMPLETE_TALK_TO_NPC, (ctx, params) -> {
            String questId = params.get("questId");
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null || questId == null) return;

            QuestInstance quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
            if (quest == null) return;

            // Mark the TALK_TO_NPC objective complete
            PhaseInstance phase = quest.getCurrentPhase();
            if (phase == null) return;

            for (ObjectiveInstance obj : phase.getObjectives()) {
                if (obj.getType() == ObjectiveType.TALK_TO_NPC && !obj.isComplete()) {
                    obj.markComplete();
                    break;
                }
            }

            // Check if all phase objectives are now complete
            if (phase.isComplete()) {
                quest.getVariableBindings().put("phase_objectives_complete", "true");
            }

            // Save quest state
            Map<String, QuestInstance> allQuests = questSystem.getStateManager().getActiveQuests(ctx.playerData());
            allQuests.put(quest.getQuestId(), quest);
            questSystem.getStateManager().saveActiveQuests(ctx.playerData(), allQuests);

            // Refresh markers: TARGET_NPC marker disappears, RETURN marker appears
            QuestMarkerProvider.refreshMarkers(
                ctx.player().getPlayerRef().getUuid(), ctx.playerData());

            LOGGER.atInfo().log("COMPLETE_TALK_TO_NPC: quest %s objective complete, return to %s",
                questId, quest.getSourceNpcId());
        });

        register(OPEN_SHOP, (ctx, params) -> {
            LOGGER.atInfo().log("OPEN_SHOP stub for %s", ctx.player().getPlayerRef().getUuid());
        });

        register(CHANGE_REPUTATION, (ctx, params) -> {
            String factionId = params.get("factionId");
            int amount = Integer.parseInt(params.getOrDefault("amount", "0"));
            var rep = ctx.playerData().getReputation();
            rep.put(factionId, rep.getOrDefault(factionId, 0) + amount);
        });

        register(EXHAUST_TOPIC, (ctx, params) -> {
            String topicId = params.get("topicId");
            ctx.topicExhauster().accept(topicId);
        });

        register(REACTIVATE_TOPIC, (ctx, params) -> {
            String topicId = params.get("topicId");
            if (topicId == null || topicId.isEmpty()) {
                LOGGER.atWarning().log("REACTIVATE_TOPIC: missing required topicId");
                return;
            }
            ctx.playerData().removeTopicExhaustion(ctx.npcId(), topicId);
            ctx.playerData().clearConsumedDecisivesForTopic(ctx.npcId(), topicId);
            String newEntryNodeId = params.get("newEntryNodeId");
            if (newEntryNodeId != null && !newEntryNodeId.isEmpty()) {
                ctx.playerData().setTopicEntryOverride(ctx.npcId(), topicId, newEntryNodeId);
            }
            ctx.topicReactivator().accept(topicId);
        });
    }

    private void triggerPOIPlacement(QuestInstance quest, Store<EntityStore> store,
                                     Ref<EntityStore> playerRef,
                                     String mobRole, int mobCount) {
        Map<String, String> bindings = quest.getVariableBindings();
        CaveVoidRegistry voidRegistry = Natural20.getInstance().getCaveVoidRegistry();
        if (voidRegistry == null) return;

        String rawX = bindings.get("poi_center_x");
        String rawZ = bindings.get("poi_center_z");
        if (rawX == null || rawZ == null) {
            LOGGER.atWarning().log("POI placement: missing center coordinates in bindings");
            bindings.put("poi_available", "false");
            return;
        }
        int poiX = Integer.parseInt(rawX);
        int poiZ = Integer.parseInt(rawZ);

        // Find and claim the void
        CaveVoidRecord void_ = voidRegistry.findAnyVoid(poiX, poiZ);
        if (void_ == null) {
            LOGGER.atWarning().log("POI placement: no void found near (%d, %d)", poiX, poiZ);
            bindings.put("poi_available", "false");
            return;
        }
        voidRegistry.claimVoid(void_, quest.getSourceSettlementId());

        // Place the dungeon prefab
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) {
            LOGGER.atWarning().log("POI placement: no default world");
            return;
        }

        Natural20.getInstance().getStructurePlacer()
            .placeAtVoid(world, void_, store)
            .whenComplete((entrance, error) -> {
                if (error != null || entrance == null) {
                    if (error != null) {
                        LOGGER.atWarning().withCause(error).log("POI placement failed for quest %s", quest.getQuestId());
                    } else {
                        LOGGER.atWarning().log("POI placement returned no entrance for quest %s", quest.getQuestId());
                    }
                    world.execute(() -> bindings.put("poi_available", "false"));
                    return;
                }
                // Update bindings and spawn mobs on the world thread (chunks are loaded from prefab paste)
                world.execute(() -> {
                    bindings.put("poi_x", String.valueOf(entrance.getX()));
                    bindings.put("poi_y", String.valueOf(entrance.getY()));
                    bindings.put("poi_z", String.valueOf(entrance.getZ()));

                    // Compute marker offset relative to actual entrance (0-80 blocks away)
                    // The center marker sits at entrance + offset; the 100-block ring circles it.
                    // Since offset max is 80 and ring radius is 100, the entrance is always inside the ring.
                    bindings.put("poi_center_x", String.valueOf(entrance.getX()));
                    bindings.put("poi_center_z", String.valueOf(entrance.getZ()));
                    Random rng = new Random(quest.getQuestId().hashCode());
                    double angle = rng.nextDouble() * 2 * Math.PI;
                    double dist = rng.nextDouble() * 80;
                    bindings.put("marker_offset_x", String.valueOf(dist * Math.cos(angle)));
                    bindings.put("marker_offset_z", String.valueOf(dist * Math.sin(angle)));

                    // Write spawn descriptor: mobs will spawn when player approaches
                    if (mobRole != null && mobCount > 0) {
                        Natural20.getInstance().getPOIPopulationListener().writeSpawnDescriptor(
                            quest, entrance.getX(), entrance.getY(), entrance.getZ(),
                            mobRole, mobCount);
                    }

                    // Save the modified quest bindings back to storage.
                    // getActiveQuests() deserializes fresh, so we must re-insert our modified quest.
                    Nat20PlayerData pd = store.getComponent(playerRef, Natural20.getPlayerDataType());
                    if (pd != null) {
                        QuestStateManager sm = Natural20.getInstance().getQuestSystem().getStateManager();
                        Map<String, QuestInstance> allQuests = sm.getActiveQuests(pd);
                        allQuests.put(quest.getQuestId(), quest);
                        sm.saveActiveQuests(pd, allQuests);

                        // Refresh markers now that we have the real entrance-relative offset
                        com.hypixel.hytale.server.core.entity.entities.Player player =
                            store.getComponent(playerRef, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                        if (player != null) {
                            QuestMarkerProvider.refreshMarkers(player.getPlayerRef().getUuid(), pd);
                        }
                    }
                });
                LOGGER.atInfo().log("POI placed for quest %s at (%d, %d, %d)",
                    quest.getQuestId(), entrance.getX(), entrance.getY(), entrance.getZ());
            });
    }

    /**
     * Remove up to 'count' items of the given type from the player's hotbar.
     * Returns the number actually removed.
     */
    @SuppressWarnings("unchecked")
    private static int consumeResources(ActionContext ctx, String itemTypeId, int requiredCount) {
        try {
            CombinedItemContainer hotbar = InventoryComponent.getCombined(
                    ctx.store(), ctx.playerRef(),
                    new ComponentType[]{InventoryComponent.Hotbar.getComponentType()});
            if (hotbar == null) return 0;
            short capacity = hotbar.getCapacity();
            int remaining = requiredCount;

            for (short slot = 0; slot < capacity && remaining > 0; slot++) {
                ItemStack stack = hotbar.getItemStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                if (!itemTypeId.equals(stack.getItemId())) continue;

                int qty = stack.getQuantity();
                if (qty <= remaining) {
                    // Take entire stack
                    hotbar.setItemStackForSlot(slot, null);
                    remaining -= qty;
                } else {
                    // Take partial stack: leave the remainder
                    hotbar.setItemStackForSlot(slot, new ItemStack(itemTypeId, qty - remaining));
                    remaining = 0;
                }
            }

            return requiredCount - remaining;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error consuming resources %s", itemTypeId);
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean consumeFetchItem(ActionContext ctx, String itemTypeId) {
        try {
            CombinedItemContainer hotbar = InventoryComponent.getCombined(
                    ctx.store(), ctx.playerRef(),
                    new ComponentType[]{InventoryComponent.Hotbar.getComponentType()});
            if (hotbar == null) return false;
            short capacity = hotbar.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = hotbar.getItemStack(slot);
                if (stack != null && !stack.isEmpty() && itemTypeId.equals(stack.getItemId())) {
                    hotbar.setItemStackForSlot(slot, null);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error consuming fetch item %s", itemTypeId);
            return false;
        }
    }

    public void register(String type, DialogueAction action) {
        actions.put(type, action);
    }

    public void execute(String type, ActionContext context, Map<String, String> params) {
        var action = actions.get(type);
        if (action == null) {
            LOGGER.atWarning().log("Unknown action type: %s", type);
            return;
        }
        action.execute(context, params);
    }
}
