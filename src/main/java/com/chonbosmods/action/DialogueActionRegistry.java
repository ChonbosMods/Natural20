package com.chonbosmods.action;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.marker.QuestMarkerManager;
import com.chonbosmods.quest.DirectionUtil;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.ObjectiveType;
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
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.chonbosmods.waypoint.QuestMarkerProvider;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    /** First half of the v2 turn-in flow: consumes objective items, claims the reward,
     *  and parks the quest in {@link com.chonbosmods.quest.QuestState#AWAITING_CONTINUATION}.
     *  Idempotent: re-firing in AWAITING_CONTINUATION is a no-op so closing the dialog
     *  mid-flow and re-clicking the topic doesn't double-consume or double-reward. */
    public static final String TURN_IN_V2 = "TURN_IN_V2";
    /** Second half of the v2 turn-in flow: advances {@code conflictCount}, sets up the
     *  next objective (POI placement, markers), and either transitions to OBJECTIVE_PENDING
     *  for the next conflict or COMPLETED. Fired by the [CONTINUE] response after the
     *  turn-in narration is shown. */
    public static final String CONTINUE_QUEST = "CONTINUE_QUEST";
    public static final String SKILL_CHECK = "SKILL_CHECK";
    public static final String FORCE_CLOSE = "FORCE_CLOSE";
    public static final String COMPLETE_TALK_TO_NPC = "COMPLETE_TALK_TO_NPC";
    public static final String OPEN_SHOP = "OPEN_SHOP";
    public static final String CHANGE_REPUTATION = "CHANGE_REPUTATION";
    public static final String EXHAUST_TOPIC = "EXHAUST_TOPIC";
    public static final String REACTIVATE_TOPIC = "REACTIVATE_TOPIC";
    /** Stamps the {@code skillcheckPassed} flag on an NPC's pre-generated quest before
     *  GIVE_QUEST fires, so TURN_IN_V2 can apply the skillcheck pass reward bonus.
     *  Fired from the pass branch of a quest's accept-phase skill check. */
    public static final String MARK_SKILLCHECK_PASSED = "MARK_SKILLCHECK_PASSED";

    // v2 quest flow constants
    private static final double BASE_REWARD_MULTIPLIER = 1.0;
    private static final double CONFLICT_REWARD_BONUS = 0.5;
    private static final double SKILLCHECK_PASS_REWARD_BONUS = 0.25;

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

            // Set state BEFORE saving so it persists correctly
            quest.setState(com.chonbosmods.quest.QuestState.OBJECTIVE_PENDING);

            // Add quest to player's active quests
            questSystem.getStateManager().addQuest(ctx.playerData(), quest);
            ctx.dispositionUpdater().accept(QuestDispositionConstants.QUEST_ACCEPTED);

            // Set up first objective: late-bind a deferred TALK_TO_NPC target if
            // needed (the registry may have new settlements since generation), then
            // do POI placement / particles for the chosen type. If late-binding
            // still fails (single-settlement world with only the quest giver),
            // setTargetNpcParticle will no-op gracefully.
            ObjectiveInstance firstObj = quest.getCurrentObjective();
            if (firstObj != null) {
                tryResolveObjective(quest, firstObj);
                ObjectiveType firstType = firstObj.getType();
                if (firstType == ObjectiveType.KILL_MOBS || firstType == ObjectiveType.FETCH_ITEM) {
                    resolveAndPlacePoi(quest, firstObj, ctx.store(), ctx.playerRef());
                } else if (firstType == ObjectiveType.PEACEFUL_FETCH) {
                    setupPeacefulFetchPoi(quest, ctx.store(), ctx.playerRef());
                } else if (firstType == ObjectiveType.TALK_TO_NPC) {
                    setTargetNpcParticle(quest.getVariableBindings(), ctx.store());
                } else {
                    quest.getVariableBindings().put("poi_available", "false");
                }
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
            // Phase 1 of the v2 turn-in flow: irreversibly consume objective items,
            // claim the reward, and park the quest in AWAITING_CONTINUATION. The
            // dialog then displays the turn-in narration and offers [CONTINUE].
            // Re-firing in AWAITING_CONTINUATION is a no-op so closing the dialog
            // mid-flow and re-clicking the topic doesn't double-consume.
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

            if (quest == null) {
                LOGGER.atWarning().log("TURN_IN_V2: no quest found for NPC %s", ctx.npcId());
                return;
            }

            // Idempotency guard: only the first call (READY_FOR_TURN_IN) consumes
            // items and claims the reward. Re-clicks in AWAITING_CONTINUATION fall
            // through silently so the player can re-read the turn-in text.
            if (quest.getState() == com.chonbosmods.quest.QuestState.AWAITING_CONTINUATION) {
                LOGGER.atFine().log("TURN_IN_V2: quest %s already in AWAITING_CONTINUATION, skipping consume/reward", quest.getQuestId());
                return;
            }
            if (quest.getState() != com.chonbosmods.quest.QuestState.READY_FOR_TURN_IN) {
                LOGGER.atWarning().log("TURN_IN_V2: quest %s not in READY_FOR_TURN_IN (state=%s)",
                    quest.getQuestId(), quest.getState());
                return;
            }

            // Consume items for the current objective
            ObjectiveInstance currentObj = quest.getCurrentObjective();
            if (currentObj != null) {
                if (currentObj.getType() == ObjectiveType.FETCH_ITEM
                        || currentObj.getType() == ObjectiveType.PEACEFUL_FETCH) {
                    String fetchItemType = quest.getVariableBindings().get("fetch_item_type");
                    if (fetchItemType != null) consumeFetchItem(ctx, fetchItemType);
                } else if (currentObj.getType() == ObjectiveType.COLLECT_RESOURCES) {
                    String resourceId = currentObj.getTargetId();
                    if (resourceId != null) consumeResources(ctx, resourceId, currentObj.getRequiredCount());
                }
            }

            // Reward (stub: gold/item/flavor live in template fields, not yet dispensed).
            // Gold dispersal and item deposit are a follow-up task.
            double multiplier = BASE_REWARD_MULTIPLIER + (quest.getConflictCount() * CONFLICT_REWARD_BONUS);
            if (quest.isSkillcheckPassed()) multiplier += SKILLCHECK_PASS_REWARD_BONUS;
            quest.claimReward(quest.getConflictCount());
            ctx.dispositionUpdater().accept(QuestDispositionConstants.QUEST_PHASE_TURNED_IN);

            // Park in AWAITING_CONTINUATION. Marker stays on the source NPC and the
            // return waypoint stays on the map until CONTINUE_QUEST runs.
            quest.setState(com.chonbosmods.quest.QuestState.AWAITING_CONTINUATION);
            saveQuest(questSystem, ctx.playerData(), quest);
            LOGGER.atInfo().log("TURN_IN_V2: quest %s phase %d turned in (multiplier=%.2f), awaiting continuation",
                quest.getQuestId(), quest.getConflictCount(), multiplier);
        });

        register(CONTINUE_QUEST, (ctx, params) -> {
            // Phase 2 of the v2 turn-in flow: advance conflictCount and set up the
            // next objective (POI, markers), or finalize the quest if no more
            // conflicts remain. Only valid in AWAITING_CONTINUATION.
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
                            && q.getState() == com.chonbosmods.quest.QuestState.AWAITING_CONTINUATION) {
                        quest = q;
                        break;
                    }
                }
            }

            if (quest == null || quest.getState() != com.chonbosmods.quest.QuestState.AWAITING_CONTINUATION) {
                LOGGER.atWarning().log("CONTINUE_QUEST: no quest awaiting continuation from NPC %s", ctx.npcId());
                return;
            }

            // Clear source NPC turn-in marker now that the player is moving on
            clearSourceNpcMarker(quest);

            // Try to advance to next conflict, or complete the quest
            boolean advanceToConflict = false;
            if (quest.hasMoreConflicts()) {
                int nextIndex = quest.getConflictCount() + 1;
                ObjectiveInstance nextObj = nextIndex < quest.getObjectives().size()
                    ? quest.getObjectives().get(nextIndex) : null;
                if (nextObj != null) {
                    advanceToConflict = tryResolveObjective(quest, nextObj);
                }
                if (!advanceToConflict) {
                    LOGGER.atWarning().log(
                        "CONTINUE_QUEST: conflict %d objective unresolvable for quest %s " +
                        "(type=%s, targetId=%s, hasPoi=%s). Skipping to resolution.",
                        nextIndex, quest.getQuestId(),
                        nextObj != null ? nextObj.getType() : "null",
                        nextObj != null ? nextObj.getTargetId() : "null",
                        nextObj != null ? nextObj.hasPoi() : "n/a");
                }
            }

            if (advanceToConflict) {
                quest.incrementConflictCount();
                quest.setState(com.chonbosmods.quest.QuestState.OBJECTIVE_PENDING);

                ObjectiveInstance newObj = quest.getCurrentObjective();
                ObjectiveType newType = newObj.getType();
                Map<String, String> bindings = quest.getVariableBindings();

                // Clear stale POI bindings from previous objective
                bindings.remove("marker_offset_x");
                bindings.remove("marker_offset_z");
                bindings.remove("poi_mob_state");
                bindings.remove("poi_mob_uuids");
                bindings.remove("poi_detached_uuids");
                bindings.remove("poi_chest_placed");

                if (newType == ObjectiveType.KILL_MOBS || newType == ObjectiveType.FETCH_ITEM) {
                    resolveAndPlacePoi(quest, newObj, ctx.store(), ctx.playerRef());
                } else {
                    bindings.put("poi_available", "false");
                    if (newType == ObjectiveType.TALK_TO_NPC) {
                        setTargetNpcParticle(bindings, ctx.store());
                    }
                }

                String summary = switch (newType) {
                    case KILL_MOBS -> "kill " + newObj.getRequiredCount() + " " + newObj.getEffectiveLabel();
                    case COLLECT_RESOURCES -> "collect " + newObj.getRequiredCount() + " " + newObj.getEffectiveLabel();
                    case FETCH_ITEM -> "retrieve " + newObj.getTargetLabel();
                    case PEACEFUL_FETCH -> "pick up " + newObj.getTargetLabel();
                    case TALK_TO_NPC -> "speak with " + newObj.getTargetLabel();
                };
                bindings.put("quest_objective_summary", summary);
                ctx.systemLogger().accept("New objective: " + summary);

                saveQuest(questSystem, ctx.playerData(), quest);
                LOGGER.atInfo().log("CONTINUE_QUEST: quest %s advanced to conflict %d/%d",
                    quest.getQuestId(), quest.getConflictCount(), quest.getMaxConflicts());
            } else {
                quest.setState(com.chonbosmods.quest.QuestState.COMPLETED);
                ctx.dispositionUpdater().accept(QuestDispositionConstants.QUEST_COMPLETED);
                questSystem.getStateManager().markQuestCompleted(ctx.playerData(), quest.getQuestId());
                ctx.systemLogger().accept("Quest completed: " + quest.getSituationId());
                LOGGER.atInfo().log("CONTINUE_QUEST: quest %s completed", quest.getQuestId());
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

            // Defer the completion banner until the dialogue UI closes so it doesn't
            // render on top of the active dialogue. The flag flip happens in
            // DialogueManager.endSession via QuestInstance.markPhaseReadyForTurnIn().
            Natural20.getInstance().getDialogueManager()
                .queueBannerOnSessionEnd(ctx.player().getPlayerRef().getUuid(), quest);

            // Save
            saveQuest(questSystem, ctx.playerData(), quest);

            // Re-evaluate target NPC's particle. target_npc_settlement is the
            // display name; cell-key lookup uses target_npc_settlement_key.
            String targetSettlementKey = quest.getVariableBindings().get("target_npc_settlement_key");
            String targetNpcName = quest.getVariableBindings().get("target_npc");
            if (targetSettlementKey != null && targetNpcName != null) {
                SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
                if (settlements != null) {
                    SettlementRecord settlement = settlements.getByCell(targetSettlementKey);
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

        register(MARK_SKILLCHECK_PASSED, (ctx, params) -> {
            // Look up the NPC's pre-generated quest and stamp the passed flag.
            // Fired from the pass branch of an accept-phase skill check, before
            // GIVE_QUEST adds the quest to the player's active list. The flag rides
            // along on the same QuestInstance reference, so TURN_IN_V2's reward
            // multiplier applies SKILLCHECK_PASS_REWARD_BONUS automatically.
            String cellKey = ctx.npcData() != null ? ctx.npcData().getSettlementCellKey() : "";
            SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
            if (settlements == null || cellKey == null || cellKey.isEmpty()) {
                LOGGER.atWarning().log("MARK_SKILLCHECK_PASSED: no settlement registry or cell key for NPC %s", ctx.npcId());
                return;
            }
            SettlementRecord settlement = settlements.getByCell(cellKey);
            if (settlement == null) {
                LOGGER.atWarning().log("MARK_SKILLCHECK_PASSED: settlement not found for cell %s", cellKey);
                return;
            }
            NpcRecord npcRecord = settlement.getNpcByName(ctx.npcId());
            if (npcRecord == null || npcRecord.getPreGeneratedQuest() == null) {
                LOGGER.atWarning().log("MARK_SKILLCHECK_PASSED: no pre-generated quest for NPC %s", ctx.npcId());
                return;
            }
            npcRecord.getPreGeneratedQuest().setSkillcheckPassed(true);
            LOGGER.atInfo().log("MARK_SKILLCHECK_PASSED: stamped pass flag on pre-generated quest for NPC %s", ctx.npcId());
        });
    }

    /**
     * Validate and resolve an objective before advancing into it.
     * For TALK_TO_NPC with deferred target: try to find a real NPC now.
     * For KILL_MOBS/FETCH_ITEM: always resolvable (void or surface fallback).
     * For COLLECT_RESOURCES: always resolvable.
     * Returns true if the objective can be activated, false to skip to resolution.
     */
    public static boolean tryResolveObjective(QuestInstance quest, ObjectiveInstance objective) {
        ObjectiveType type = objective.getType();

        return switch (type) {
            case COLLECT_RESOURCES -> true;
            case KILL_MOBS, FETCH_ITEM -> true; // resolveAndPlacePoi handles void + surface fallback
            case PEACEFUL_FETCH -> true;
            case TALK_TO_NPC -> tryResolveDeferredTalkToNpc(quest, objective);
        };
    }

    /**
     * Late-bind a deferred TALK_TO_NPC target. Tries the nearest other settlement
     * first; if none has NPCs (e.g., single-settlement world), falls back to a
     * different NPC in the source settlement so the quest is still completable.
     *
     * <p>The target_npc trio (name, role, settlement) and the internal
     * target_npc_settlement_key are always written together so authors can safely
     * write "{target_npc}, the {target_npc_role} from {target_npc_settlement}" and
     * downstream lookups by cell key always succeed.
     *
     * <p>Returns true if the objective is resolved (or was already resolved),
     * false if no candidate NPC could be found at all (source settlement only
     * contains the quest giver and no other settlements have NPCs).
     */
    public static boolean tryResolveDeferredTalkToNpc(QuestInstance quest, ObjectiveInstance objective) {
        if (!"deferred_npc".equals(objective.getTargetId())) {
            return true; // already resolved at generation time
        }

        Map<String, String> bindings = quest.getVariableBindings();
        double npcX = 0, npcZ = 0;
        try {
            npcX = Double.parseDouble(bindings.getOrDefault("npc_x", "0"));
            npcZ = Double.parseDouble(bindings.getOrDefault("npc_z", "0"));
        } catch (NumberFormatException ignored) {}

        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null) {
            LOGGER.atWarning().log("tryResolveDeferredTalkToNpc: no settlement registry for quest %s",
                quest.getQuestId());
            return false;
        }

        // 1. Try nearest OTHER settlement with NPCs.
        SettlementRecord nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (SettlementRecord r : settlements.getAll().values()) {
            if (r.getCellKey().equals(quest.getSourceSettlementId())) continue;
            if (r.getNpcs().isEmpty()) continue;
            double dx = r.getPosX() - npcX;
            double dz = r.getPosZ() - npcZ;
            double dist = dx * dx + dz * dz;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = r;
            }
        }

        SettlementRecord chosenSettlement = nearest;
        NpcRecord chosenNpc = null;

        if (chosenSettlement != null) {
            // Pick deterministically from the nearest other settlement.
            chosenNpc = chosenSettlement.getNpcs().get(
                new Random(quest.getQuestId().hashCode()).nextInt(chosenSettlement.getNpcs().size()));
        } else {
            // 2. Fallback: pick a non-quest-giver NPC in the source settlement so
            // single-settlement worlds (e.g., player teleported in before others
            // generated) still produce a completable TALK_TO_NPC objective.
            SettlementRecord source = settlements.getByCell(quest.getSourceSettlementId());
            if (source != null) {
                List<NpcRecord> candidates = new ArrayList<>();
                for (NpcRecord n : source.getNpcs()) {
                    if (!n.getGeneratedName().equals(quest.getSourceNpcId())) {
                        candidates.add(n);
                    }
                }
                if (!candidates.isEmpty()) {
                    chosenNpc = candidates.get(
                        new Random(quest.getQuestId().hashCode()).nextInt(candidates.size()));
                    chosenSettlement = source;
                }
            }
        }

        if (chosenSettlement == null || chosenNpc == null) {
            LOGGER.atWarning().log(
                "tryResolveDeferredTalkToNpc: no candidate NPC for quest %s: " +
                "no other settlement with NPCs near (%.0f, %.0f) and source settlement has no other NPCs. " +
                "Total settlements: %d",
                quest.getQuestId(), npcX, npcZ, settlements.getAll().size());
            return false;
        }

        objective.setTargetId(chosenNpc.getGeneratedName());
        objective.setTargetLabel(chosenNpc.getGeneratedName());
        objective.setLocationId(chosenSettlement.getCellKey());
        bindings.put("target_npc", chosenNpc.getGeneratedName());
        bindings.put("target_npc_role", chosenNpc.getDisplayRole());
        bindings.put("target_npc_settlement", chosenSettlement.deriveName());
        bindings.put("target_npc_settlement_key", chosenSettlement.getCellKey());

        boolean fellBackToSource = chosenSettlement.getCellKey().equals(quest.getSourceSettlementId());
        LOGGER.atInfo().log("tryResolveDeferredTalkToNpc: resolved quest %s target=%s settlement=%s%s",
            quest.getQuestId(), chosenNpc.getGeneratedName(), chosenSettlement.getCellKey(),
            fellBackToSource ? " (same-settlement fallback)" : "");
        return true;
    }

    /**
     * Unified POI resolution: finds a cave void and places a dungeon.
     * - If the objective has pre-claimed POI data, looks up that void directly.
     * - Otherwise, finds the nearest unclaimed void at runtime and claims it.
     * - If no void is found at all, auto-completes the objective (graceful degradation).
     * The void record is passed directly to placement: no re-search, no double-claim.
     */
    /**
     * Unified POI resolution: finds a cave void or surface location and places a dungeon.
     * 1. Pre-claimed void (from generation): look up by coords
     * 2. Runtime void: find nearest unclaimed, claim, pass directly to placement
     * 3. Surface fallback: pick random point 200-400 blocks away, paste prefab at surface
     * No re-search, no double-claim. Void record always passed directly.
     */
    private void resolveAndPlacePoi(QuestInstance quest, ObjectiveInstance objective,
                                     Store<EntityStore> store, Ref<EntityStore> playerRef) {
        Map<String, String> bindings = quest.getVariableBindings();

        double npcX = 0, npcZ = 0;
        try {
            npcX = Double.parseDouble(bindings.getOrDefault("npc_x", "0"));
            npcZ = Double.parseDouble(bindings.getOrDefault("npc_z", "0"));
        } catch (NumberFormatException ignored) {}

        // --- Strategy 1: pre-claimed void from generation ---
        CaveVoidRecord void_ = null;
        CaveVoidRegistry voidRegistry = Natural20.getInstance().getCaveVoidRegistry();
        if (objective.hasPoi() && voidRegistry != null) {
            void_ = voidRegistry.findVoidAt(
                objective.getPoiCenterX(), objective.getPoiCenterY(), objective.getPoiCenterZ());
            if (void_ == null) {
                LOGGER.atWarning().log("resolveAndPlacePoi: pre-claimed void not found at (%d,%d,%d), trying runtime",
                    objective.getPoiCenterX(), objective.getPoiCenterY(), objective.getPoiCenterZ());
            }
        }

        // --- Strategy 2: runtime void discovery ---
        if (void_ == null && voidRegistry != null) {
            void_ = voidRegistry.findNearbyVoid(npcX, npcZ, 200, 600);
            if (void_ != null) {
                voidRegistry.claimVoid(void_, quest.getSourceSettlementId());
                String enemyTypeId = bindings.getOrDefault("enemy_type_id", "Skeleton");
                int spawnCount = objective.getType() == ObjectiveType.KILL_MOBS ? 4 : 3;
                objective.setPoi(void_.getCenterX(), void_.getCenterY(), void_.getCenterZ(),
                    "KILL_MOBS:" + enemyTypeId + ":" + spawnCount);
                LOGGER.atInfo().log("resolveAndPlacePoi: runtime void at (%d,%d,%d) for quest %s",
                    void_.getCenterX(), void_.getCenterY(), void_.getCenterZ(), quest.getQuestId());
            }
        }

        // --- Strategy 3: surface fallback ---
        if (void_ == null) {
            LOGGER.atInfo().log("resolveAndPlacePoi: no void found, using surface fallback for quest %s",
                quest.getQuestId());
            placeSurfacePoi(quest, objective, store, playerRef, npcX, npcZ);
            return;
        }

        // --- Place dungeon at void (strategies 1 & 2) ---
        placePoiAtVoid(quest, objective, void_, store, playerRef);
    }

    /**
     * Place dungeon at a cave void. Void record is passed directly: no re-search.
     */
    private void placePoiAtVoid(QuestInstance quest, ObjectiveInstance objective,
                                 CaveVoidRecord void_, Store<EntityStore> store,
                                 Ref<EntityStore> playerRef) {
        Map<String, String> bindings = quest.getVariableBindings();
        bindings.put("poi_available", "true");
        bindings.put("poi_center_x", String.valueOf(void_.getCenterX()));
        bindings.put("poi_center_z", String.valueOf(void_.getCenterZ()));

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        Natural20.getInstance().getStructurePlacer()
            .placeAtVoid(world, void_, store)
            .whenComplete((entrance, error) -> {
                if (error != null || entrance == null) {
                    if (error != null) {
                        LOGGER.atWarning().withCause(error).log("POI void placement failed for quest %s", quest.getQuestId());
                    }
                    world.execute(() -> bindings.put("poi_available", "false"));
                    return;
                }
                world.execute(() -> finalizePlacement(quest, objective, entrance, store, playerRef));
            });
    }

    /**
     * Surface POI fallback: use a pre-placed fallback POI from the settlement if available,
     * otherwise place the surfaceFallbackPrefab at a random point 200-400 blocks away.
     */
    private void placeSurfacePoi(QuestInstance quest, ObjectiveInstance objective,
                                  Store<EntityStore> store, Ref<EntityStore> playerRef,
                                  double npcX, double npcZ) {
        Map<String, String> bindings = quest.getVariableBindings();
        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        // Build population spec if not already set
        if (objective.getPopulationSpec() == null) {
            String enemyTypeId = bindings.getOrDefault("enemy_type_id", "Skeleton");
            int spawnCount = objective.getType() == ObjectiveType.KILL_MOBS ? 4 : 3;
            objective.setPoi(0, 0, 0, "KILL_MOBS:" + enemyTypeId + ":" + spawnCount);
        }

        // Try to claim a pre-placed surface fallback POI from the settlement
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements != null && quest.getSourceSettlementId() != null) {
            SettlementRecord settlement = settlements.getByCell(quest.getSourceSettlementId());
            if (settlement != null) {
                int[] prePlaced = settlement.claimSurfaceFallbackPoi();
                if (prePlaced != null) {
                    LOGGER.atInfo().log("placeSurfacePoi: using pre-placed fallback at (%d,%d,%d) for quest %s",
                        prePlaced[0], prePlaced[1], prePlaced[2], quest.getQuestId());
                    settlements.saveAsync();
                    Vector3i entrance = new Vector3i(prePlaced[0], prePlaced[1], prePlaced[2]);
                    objective.setPoi(prePlaced[0], prePlaced[1], prePlaced[2], objective.getPopulationSpec());
                    bindings.put("poi_available", "true");
                    bindings.put("poi_center_x", String.valueOf(prePlaced[0]));
                    bindings.put("poi_center_z", String.valueOf(prePlaced[2]));
                    finalizePlacement(quest, objective, entrance, store, playerRef);
                    return;
                }
            }
        }

        // No pre-placed POI available: place one now at a random surface point
        Random rng = new Random(quest.getQuestId().hashCode() + quest.getConflictCount());
        double angle = rng.nextDouble() * 2 * Math.PI;
        double dist = 200 + rng.nextDouble() * 200;
        int targetX = (int) (npcX + dist * Math.cos(angle));
        int targetZ = (int) (npcZ + dist * Math.sin(angle));

        objective.setPoi(targetX, 0, targetZ, objective.getPopulationSpec());
        bindings.put("poi_available", "true");
        bindings.put("poi_center_x", String.valueOf(targetX));
        bindings.put("poi_center_z", String.valueOf(targetZ));

        Natural20.getInstance().getStructurePlacer()
            .placeAtSurface(world, targetX, targetZ, store)
            .whenComplete((entrance, error) -> {
                if (error != null || entrance == null) {
                    if (error != null) {
                        LOGGER.atWarning().withCause(error).log("Surface POI placement failed for quest %s", quest.getQuestId());
                    }
                    world.execute(() -> bindings.put("poi_available", "false"));
                    return;
                }
                world.execute(() -> finalizePlacement(quest, objective, entrance, store, playerRef));
            });
    }

    /**
     * Shared post-placement logic: set bindings, compute marker offset, write spawn descriptor, save.
     */
    private void finalizePlacement(QuestInstance quest, ObjectiveInstance objective,
                                    Vector3i entrance, Store<EntityStore> store,
                                    Ref<EntityStore> playerRef) {
        Map<String, String> bindings = quest.getVariableBindings();
        bindings.put("poi_x", String.valueOf(entrance.getX()));
        bindings.put("poi_y", String.valueOf(entrance.getY()));
        bindings.put("poi_z", String.valueOf(entrance.getZ()));
        bindings.put("poi_center_x", String.valueOf(entrance.getX()));
        bindings.put("poi_center_z", String.valueOf(entrance.getZ()));

        Random rng = new Random(quest.getQuestId().hashCode() + quest.getConflictCount());
        double angle = rng.nextDouble() * 2 * Math.PI;
        double dist = rng.nextDouble() * 80;
        bindings.put("marker_offset_x", String.valueOf(dist * Math.cos(angle)));
        bindings.put("marker_offset_z", String.valueOf(dist * Math.sin(angle)));

        // Parse population spec and write spawn descriptor
        String popSpec = objective.getPopulationSpec();
        if (popSpec != null && !popSpec.equals("NONE")) {
            int firstColon = popSpec.indexOf(':');
            int lastColon = popSpec.lastIndexOf(':');
            if (firstColon > 0 && lastColon > firstColon) {
                String mobRole = popSpec.substring(firstColon + 1, lastColon);
                int mobCount = Integer.parseInt(popSpec.substring(lastColon + 1));
                if (mobCount > 0) {
                    Natural20.getInstance().getPOIPopulationListener().writeSpawnDescriptor(
                        quest, entrance.getX(), entrance.getY(), entrance.getZ(),
                        mobRole, mobCount);
                }
            }
        }

        // Save modified quest bindings
        Nat20PlayerData pd = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (pd != null) {
            QuestStateManager sm = Natural20.getInstance().getQuestSystem().getStateManager();
            Map<String, QuestInstance> allQuests = sm.getActiveQuests(pd);
            allQuests.put(quest.getQuestId(), quest);
            sm.saveActiveQuests(pd, allQuests);

            com.hypixel.hytale.server.core.entity.entities.Player player =
                store.getComponent(playerRef, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player != null) {
                QuestMarkerProvider.refreshMarkers(player.getPlayerRef().getUuid(), pd);
            }
        }

        LOGGER.atInfo().log("POI placed for quest %s at (%d, %d, %d)",
            quest.getQuestId(), entrance.getX(), entrance.getY(), entrance.getZ());
    }

    /**
     * Set up POI bindings for a PEACEFUL_FETCH objective so the existing
     * {@link com.chonbosmods.quest.POIProximitySystem} places a chest when the
     * player arrives at the target settlement. Falls back to the quest-giver's
     * own settlement if no cross-settlement target exists.
     */
    private void setupPeacefulFetchPoi(QuestInstance quest,
                                        Store<EntityStore> store,
                                        Ref<EntityStore> playerRef) {
        Map<String, String> bindings = quest.getVariableBindings();
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null) {
            bindings.put("poi_available", "false");
            return;
        }

        // Try target settlement first, fall back to quest giver's own settlement
        String targetKey = bindings.get("target_npc_settlement_key");
        SettlementRecord target = targetKey != null ? settlements.getByCell(targetKey) : null;
        if (target == null) {
            target = settlements.getByCell(quest.getSourceSettlementId());
        }
        if (target == null) {
            LOGGER.atWarning().log("PEACEFUL_FETCH: no settlement found for quest %s", quest.getQuestId());
            bindings.put("poi_available", "false");
            return;
        }

        int sx = (int) target.getPosX();
        int sy = (int) target.getPosY();
        int sz = (int) target.getPosZ();

        bindings.put("poi_x", String.valueOf(sx));
        bindings.put("poi_y", String.valueOf(sy));
        bindings.put("poi_z", String.valueOf(sz));
        bindings.put("poi_available", "true");
        bindings.put("poi_mob_state", "PENDING");

        // Save so proximity system picks up the bindings
        Nat20PlayerData pd = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (pd != null) {
            QuestStateManager sm = Natural20.getInstance().getQuestSystem().getStateManager();
            Map<String, QuestInstance> allQuests = sm.getActiveQuests(pd);
            allQuests.put(quest.getQuestId(), quest);
            sm.saveActiveQuests(pd, allQuests);

            com.hypixel.hytale.server.core.entity.entities.Player player =
                store.getComponent(playerRef, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player != null) {
                QuestMarkerProvider.refreshMarkers(player.getPlayerRef().getUuid(), pd);
            }
        }

        LOGGER.atInfo().log("PEACEFUL_FETCH: POI set at settlement (%d, %d, %d) for quest %s",
            sx, sy, sz, quest.getQuestId());
    }

    /**
     * Remove up to 'count' items of the given type from the player's inventory.
     * Returns the number actually removed.
     */
    @SuppressWarnings("unchecked")
    private static int consumeResources(ActionContext ctx, String itemTypeId, int requiredCount) {
        try {
            CombinedItemContainer inventory = InventoryComponent.getCombined(
                    ctx.store(), ctx.playerRef(),
                    InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
            if (inventory == null) return 0;
            short capacity = inventory.getCapacity();
            int remaining = requiredCount;

            for (short slot = 0; slot < capacity && remaining > 0; slot++) {
                ItemStack stack = inventory.getItemStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                if (!itemTypeId.equals(stack.getItemId())) continue;

                int qty = stack.getQuantity();
                if (qty <= remaining) {
                    inventory.setItemStackForSlot(slot, null);
                    remaining -= qty;
                } else {
                    inventory.setItemStackForSlot(slot, new ItemStack(itemTypeId, qty - remaining));
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
            CombinedItemContainer inventory = InventoryComponent.getCombined(
                    ctx.store(), ctx.playerRef(),
                    InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
            if (inventory == null) return false;
            short capacity = inventory.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = inventory.getItemStack(slot);
                if (stack != null && !stack.isEmpty() && itemTypeId.equals(stack.getItemId())) {
                    inventory.setItemStackForSlot(slot, null);
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
     * Set QUEST_TURN_IN ("?") particle on the target NPC for a TALK_TO_NPC objective.
     * Persists to NpcRecord so marker survives chunk reload.
     */
    private static void setTargetNpcParticle(Map<String, String> bindings, Store<EntityStore> store) {
        // target_npc_settlement is the human-readable display name; the cell key
        // (used for registry lookup) lives in target_npc_settlement_key.
        String targetSettlementKey = bindings.get("target_npc_settlement_key");
        String targetNpcName = bindings.get("target_npc");
        if (targetSettlementKey == null || targetNpcName == null) return;

        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null) return;

        SettlementRecord settlement = settlements.getByCell(targetSettlementKey);
        if (settlement == null) return;

        NpcRecord targetNpc = settlement.getNpcByName(targetNpcName);
        if (targetNpc == null) return;

        // Persist marker state on NpcRecord so it survives chunk reload
        targetNpc.setMarkerState("QUEST_TURN_IN");
        settlements.saveAsync();

        if (targetNpc.getEntityUUID() != null) {
            QuestMarkerManager.INSTANCE.syncMarker(
                targetNpc.getEntityUUID(),
                Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
        }
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
