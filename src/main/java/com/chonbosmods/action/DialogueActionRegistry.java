package com.chonbosmods.action;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.ObjectiveType;
import com.chonbosmods.quest.PhaseInstance;
import com.chonbosmods.quest.POIPopulationListener;
import com.chonbosmods.quest.QuestStateManager;
import com.chonbosmods.quest.QuestDispositionConstants;
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
    public static final String TURN_IN_V2 = "TURN_IN_V2";
    public static final String SKILL_CHECK = "SKILL_CHECK";
    public static final String FORCE_CLOSE = "FORCE_CLOSE";
    public static final String COMPLETE_TALK_TO_NPC = "COMPLETE_TALK_TO_NPC";
    public static final String OPEN_SHOP = "OPEN_SHOP";
    public static final String CHANGE_REPUTATION = "CHANGE_REPUTATION";
    public static final String EXHAUST_TOPIC = "EXHAUST_TOPIC";
    public static final String REACTIVATE_TOPIC = "REACTIVATE_TOPIC";

    // v2 quest flow constants
    private static final double CONFLICT_1_CHANCE = 0.40;
    private static final double CONFLICT_2_CHANCE = 0.10;
    private static final int MAX_CONFLICTS = 2;
    private static final double BASE_REWARD_MULTIPLIER = 1.0;
    private static final double CONFLICT_REWARD_BONUS = 0.5;
    private static final double SKILLCHECK_PASS_REWARD_BONUS = 0.25;
    private static final double SKILLCHECK_PASS_CHANCE = 0.50;
    private static final int SKILLCHECK_PASS_DISPOSITION_BONUS = 5;
    private static final int SKILLCHECK_FAIL_DISPOSITION_PENALTY = -2;

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

            // Determine if the exposition objective needs a hostile POI
            ObjectiveInstance firstObj = quest.getCurrentObjective();
            ObjectiveType firstObjType = firstObj != null ? firstObj.getType() : null;
            boolean needsPoi = firstObjType == ObjectiveType.KILL_MOBS
                || (firstObjType == ObjectiveType.FETCH_ITEM
                    && "hostile".equals(quest.getVariableBindings().get("fetch_variant")));

            // Clear POI bindings BEFORE saving if objective doesn't need a hostile location
            if (!needsPoi) {
                quest.getVariableBindings().put("poi_available", "false");
                quest.getVariableBindings().remove("marker_offset_x");
                quest.getVariableBindings().remove("marker_offset_z");
            }

            // Set state BEFORE saving so it persists correctly
            quest.setState(com.chonbosmods.quest.QuestState.ACTIVE_OBJECTIVE);

            // Add quest to player's active quests
            questSystem.getStateManager().addQuest(ctx.playerData(), quest);
            ctx.dispositionUpdater().accept(QuestDispositionConstants.QUEST_ACCEPTED);

            // Trigger POI placement only for objectives that need a hostile location
            if (needsPoi && "true".equals(quest.getVariableBindings().get("poi_available"))) {
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

            // Consume the pre-generated quest so it can't be given again
            npcRecord.setPreGeneratedQuest(null);
            settlements.saveAsync();

            // Clear quest marker particle above this NPC
            if (npcRecord.getEntityUUID() != null) {
                if (ctx.npcData() != null) {
                    ctx.npcData().setQuestMarkerState(
                        com.chonbosmods.data.Nat20NpcData.QuestMarkerState.NONE);
                }
                com.chonbosmods.marker.QuestMarkerManager.INSTANCE.syncMarker(
                    npcRecord.getEntityUUID(),
                    com.chonbosmods.data.Nat20NpcData.QuestMarkerState.NONE);
            }

            // Update waypoint marker cache
            QuestMarkerProvider.refreshMarkers(
                ctx.player().getPlayerRef().getUuid(), ctx.playerData());
        });

        register(TURN_IN_V2, (ctx, params) -> {
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null) return;

            String questId = params.get("questId");
            QuestInstance quest;
            if (questId != null) {
                quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
            } else {
                quest = null;
                for (QuestInstance q : questSystem.getStateManager().getActiveQuests(ctx.playerData()).values()) {
                    if (ctx.npcId().equals(q.getSourceNpcId())
                            && q.getState() == com.chonbosmods.quest.QuestState.READY_FOR_TURN_IN) {
                        quest = q;
                        break;
                    }
                }
            }

            if (quest == null || quest.getState() != com.chonbosmods.quest.QuestState.READY_FOR_TURN_IN) {
                LOGGER.atWarning().log("TURN_IN_V2: no quest ready for turn-in from NPC %s", ctx.npcId());
                return;
            }

            // Consume items for the current objective
            ObjectiveInstance currentObj = quest.getCurrentObjective();
            if (currentObj != null) {
                if (currentObj.getType() == ObjectiveType.FETCH_ITEM) {
                    String fetchItemType = quest.getVariableBindings().get("fetch_item_type");
                    if (fetchItemType != null) consumeFetchItem(ctx, fetchItemType);
                } else if (currentObj.getType() == ObjectiveType.COLLECT_RESOURCES) {
                    String resourceId = currentObj.getTargetId();
                    if (resourceId != null) consumeResources(ctx, resourceId, currentObj.getRequiredCount());
                }
            }

            // Reward (stub: multiplier computed but not yet dispensed)
            double multiplier = BASE_REWARD_MULTIPLIER + (quest.getConflictCount() * CONFLICT_REWARD_BONUS);
            if (quest.isSkillcheckPassed()) multiplier += SKILLCHECK_PASS_REWARD_BONUS;
            quest.claimReward(quest.getConflictCount());
            ctx.dispositionUpdater().accept(QuestDispositionConstants.QUEST_PHASE_TURNED_IN);
            LOGGER.atInfo().log("TURN_IN_V2: quest %s turn-in at conflict %d (multiplier %.2f)",
                quest.getQuestId(), quest.getConflictCount(), multiplier);

            // Roll for conflict
            boolean conflictTriggered = rollConflict(quest);

            // Clear source NPC turn-in marker
            clearSourceNpcMarker(quest);

            if (conflictTriggered) {
                quest.incrementConflictCount();
                quest.setState(com.chonbosmods.quest.QuestState.ACTIVE_OBJECTIVE);

                // Resolve POI for new objective if needed
                ObjectiveInstance newObj = quest.getCurrentObjective();
                if (newObj != null) {
                    ObjectiveType newType = newObj.getType();
                    Map<String, String> bindings = quest.getVariableBindings();
                    boolean needsPoi = newType == ObjectiveType.KILL_MOBS
                        || (newType == ObjectiveType.FETCH_ITEM
                            && "hostile".equals(bindings.get("fetch_variant")));
                    if (needsPoi) {
                        resolveNewHostilePoi(quest, ctx.store(), ctx.playerRef(), bindings);
                    } else {
                        bindings.put("poi_available", "false");
                        bindings.remove("marker_offset_x");
                        bindings.remove("marker_offset_z");
                        bindings.remove("poi_mob_state");
                        bindings.remove("poi_mob_uuids");
                        bindings.remove("poi_detached_uuids");
                        if (newType == ObjectiveType.TALK_TO_NPC) {
                            setTargetNpcParticle(bindings, ctx.store());
                        }
                    }
                    // Build objective summary
                    String summary = switch (newType) {
                        case KILL_MOBS -> "kill " + newObj.getRequiredCount() + " " + newObj.getEffectiveLabel();
                        case COLLECT_RESOURCES -> "collect " + newObj.getRequiredCount() + " " + newObj.getEffectiveLabel();
                        case FETCH_ITEM -> "hostile".equals(bindings.get("fetch_variant"))
                            ? "retrieve " + newObj.getTargetLabel() + " from " + bindings.getOrDefault("subject_name", "the area")
                            : "recover " + newObj.getTargetLabel();
                        case TALK_TO_NPC -> "speak with " + newObj.getTargetLabel();
                    };
                    bindings.put("quest_objective_summary", summary);
                }

                // Save and set dialogue continuation to conflict node
                saveQuest(questSystem, ctx.playerData(), quest);
                params.put("nextNode", params.get("conflictNode"));
                LOGGER.atInfo().log("TURN_IN_V2: quest %s conflict %d triggered",
                    quest.getQuestId(), quest.getConflictCount());
            } else {
                quest.setState(com.chonbosmods.quest.QuestState.COMPLETED);
                ctx.dispositionUpdater().accept(QuestDispositionConstants.QUEST_COMPLETED);
                questSystem.getStateManager().markQuestCompleted(ctx.playerData(), quest.getQuestId());
                ctx.systemLogger().accept("Quest completed: " + quest.getSituationId());

                // Set dialogue continuation to resolution node
                params.put("nextNode", params.get("resolutionNode"));
                LOGGER.atInfo().log("TURN_IN_V2: quest %s completed (no conflict)", quest.getQuestId());
            }

            QuestMarkerProvider.refreshMarkers(
                ctx.player().getPlayerRef().getUuid(), ctx.playerData());
        });

        register(COMPLETE_TALK_TO_NPC, (ctx, params) -> {
            String questId = params.get("questId");
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null || questId == null) return;

            QuestInstance quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
            if (quest == null) return;

            ObjectiveInstance obj = quest.getCurrentObjective();
            if (obj == null || obj.getType() != ObjectiveType.TALK_TO_NPC || obj.isComplete()) return;

            obj.markComplete();
            quest.setState(com.chonbosmods.quest.QuestState.READY_FOR_TURN_IN);

            // Save
            saveQuest(questSystem, ctx.playerData(), quest);

            // Re-evaluate target NPC's particle
            String targetSettlement = quest.getVariableBindings().get("target_npc_settlement");
            String targetNpcName = quest.getVariableBindings().get("target_npc");
            if (targetSettlement != null && targetNpcName != null) {
                SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
                if (settlements != null) {
                    SettlementRecord settlement = settlements.getByCell(targetSettlement);
                    if (settlement != null) {
                        NpcRecord targetNpc = settlement.getNpcByName(targetNpcName);
                        if (targetNpc != null && targetNpc.getEntityUUID() != null) {
                            QuestMarkerManager.INSTANCE.evaluateAndApply(
                                targetNpc.getEntityUUID(), targetNpc);
                        }
                    }
                }
            }

            // Set source NPC turn-in marker
            SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
            if (settlements != null && quest.getSourceSettlementId() != null) {
                SettlementRecord sourceSettlement = settlements.getByCell(quest.getSourceSettlementId());
                if (sourceSettlement != null) {
                    NpcRecord sourceNpc = sourceSettlement.getNpcByName(quest.getSourceNpcId());
                    if (sourceNpc != null) {
                        sourceNpc.setMarkerState("QUEST_TURN_IN");
                        settlements.saveAsync();
                        if (sourceNpc.getEntityUUID() != null) {
                            QuestMarkerManager.INSTANCE.syncMarker(
                                sourceNpc.getEntityUUID(),
                                Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
                        }
                    }
                }
            }

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

    /**
     * Attempt to find a new cave void for a hostile POI phase.
     * If no void is found, auto-complete the phase (graceful degradation).
     */
    private void resolveNewHostilePoi(QuestInstance quest, Store<EntityStore> store,
                                       Ref<EntityStore> playerRef,
                                       Map<String, String> bindings) {
        CaveVoidRegistry voidRegistry = Natural20.getInstance().getCaveVoidRegistry();
        if (voidRegistry == null) {
            LOGGER.atWarning().log("resolveNewHostilePoi: no void registry, auto-completing phase");
            bindings.put("phase_objectives_complete", "true");
            bindings.put("poi_available", "false");
            return;
        }

        double npcX = 0, npcZ = 0;
        try {
            npcX = Double.parseDouble(bindings.getOrDefault("npc_x", "0"));
            npcZ = Double.parseDouble(bindings.getOrDefault("npc_z", "0"));
        } catch (NumberFormatException ignored) {}

        var newVoid = voidRegistry.findNearbyVoid(npcX, npcZ, 200, 600);
        if (newVoid == null) {
            LOGGER.atWarning().log("resolveNewHostilePoi: no unclaimed void found for quest %s, auto-completing phase",
                quest.getQuestId());
            bindings.put("phase_objectives_complete", "true");
            bindings.put("poi_available", "false");
            bindings.remove("marker_offset_x");
            bindings.remove("marker_offset_z");
            return;
        }

        // Claim the void so triggerPOIPlacement finds the same one
        voidRegistry.claimVoid(newVoid, quest.getSourceSettlementId());

        // Update POI bindings to the new void
        bindings.put("poi_available", "true");
        bindings.put("poi_center_x", String.valueOf(newVoid.getCenterX()));
        bindings.put("poi_center_y", String.valueOf(newVoid.getCenterY()));
        bindings.put("poi_center_z", String.valueOf(newVoid.getCenterZ()));
        bindings.put("poi_mob_state", "PENDING");
        bindings.put("poi_mob_uuids", "");
        bindings.remove("poi_detached_uuids");

        // Parse population spec for the new phase
        String popSpec = bindings.get("poi_population_spec");
        String mobRole = null;
        int mobCount = 0;
        if (popSpec != null && !popSpec.equals("NONE")) {
            try {
                int firstColon = popSpec.indexOf(':');
                int lastColon = popSpec.lastIndexOf(':');
                if (firstColon > 0 && lastColon > firstColon) {
                    mobRole = popSpec.substring(firstColon + 1, lastColon);
                    mobCount = Integer.parseInt(popSpec.substring(lastColon + 1));
                }
            } catch (NumberFormatException e) {
                LOGGER.atWarning().log("resolveNewHostilePoi: malformed population spec '%s'", popSpec);
            }
        }

        triggerPOIPlacement(quest, store, playerRef, mobRole, mobCount);
    }

    /**
     * Set QUEST_AVAILABLE ("!") particle on the target NPC for a TALK_TO_NPC objective.
     */
    private static void setTargetNpcParticle(Map<String, String> bindings, Store<EntityStore> store) {
        String targetSettlement = bindings.get("target_npc_settlement");
        String targetNpcName = bindings.get("target_npc");
        if (targetSettlement == null || targetNpcName == null) return;

        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null) return;

        SettlementRecord settlement = settlements.getByCell(targetSettlement);
        if (settlement == null) return;

        NpcRecord targetNpc = settlement.getNpcByName(targetNpcName);
        if (targetNpc == null || targetNpc.getEntityUUID() == null) return;

        QuestMarkerManager.INSTANCE.syncMarker(
            targetNpc.getEntityUUID(),
            Nat20NpcData.QuestMarkerState.QUEST_AVAILABLE);

        // Update NPC data component if entity is loaded
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;
        Ref<EntityStore> npcRef = world.getEntityRef(targetNpc.getEntityUUID());
        if (npcRef == null) return;
        Nat20NpcData npcData = store.getComponent(npcRef, Natural20.getNpcDataType());
        if (npcData != null) {
            npcData.setQuestMarkerState(Nat20NpcData.QuestMarkerState.QUEST_AVAILABLE);
        }
    }

    private boolean rollConflict(QuestInstance quest) {
        if (quest.getConflictCount() >= MAX_CONFLICTS) return false;
        double chance = quest.getConflictCount() == 0 ? CONFLICT_1_CHANCE : CONFLICT_2_CHANCE;
        boolean result = new Random().nextDouble() < chance;
        LOGGER.atInfo().log("rollConflict: quest %s conflict %d, chance %.0f%%, result: %s",
            quest.getQuestId(), quest.getConflictCount(), chance * 100, result);
        return result;
    }

    private void clearSourceNpcMarker(QuestInstance quest) {
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null || quest.getSourceSettlementId() == null) return;
        SettlementRecord settlement = settlements.getByCell(quest.getSourceSettlementId());
        if (settlement == null) return;
        NpcRecord sourceNpc = settlement.getNpcByName(quest.getSourceNpcId());
        if (sourceNpc == null) return;
        sourceNpc.setMarkerState(null);
        settlements.saveAsync();
        if (sourceNpc.getEntityUUID() != null) {
            QuestMarkerManager.INSTANCE.evaluateAndApply(sourceNpc.getEntityUUID(), sourceNpc);
        }
    }

    private static void saveQuest(QuestSystem questSystem, Nat20PlayerData playerData, QuestInstance quest) {
        Map<String, QuestInstance> allQuests = questSystem.getStateManager().getActiveQuests(playerData);
        allQuests.put(quest.getQuestId(), quest);
        questSystem.getStateManager().saveActiveQuests(playerData, allQuests);
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
