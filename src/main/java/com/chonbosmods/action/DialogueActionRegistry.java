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
import com.hypixel.hytale.math.vector.Vector3i;
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

            // Set state BEFORE saving so it persists correctly
            quest.setState(com.chonbosmods.quest.QuestState.OBJECTIVE_PENDING);

            // Add quest to player's active quests
            questSystem.getStateManager().addQuest(ctx.playerData(), quest);
            ctx.dispositionUpdater().accept(QuestDispositionConstants.QUEST_ACCEPTED);

            // Set up first objective: POI placement, markers, or particles
            ObjectiveInstance firstObj = quest.getCurrentObjective();
            if (firstObj != null) {
                ObjectiveType firstType = firstObj.getType();
                if (firstType == ObjectiveType.KILL_MOBS || firstType == ObjectiveType.FETCH_ITEM) {
                    resolveAndPlacePoi(quest, firstObj, ctx.store(), ctx.playerRef());
                } else if (firstType == ObjectiveType.TALK_TO_NPC) {
                    setTargetNpcParticle(quest.getVariableBindings(), ctx.store());
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

            // Clear source NPC turn-in marker
            clearSourceNpcMarker(quest);

            // Try to advance to next conflict, or complete the quest
            boolean advanceToConflict = false;
            if (quest.hasMoreConflicts()) {
                // Peek at next objective and try to resolve it before committing
                int nextIndex = quest.getConflictCount() + 1;
                ObjectiveInstance nextObj = nextIndex < quest.getObjectives().size()
                    ? quest.getObjectives().get(nextIndex) : null;
                if (nextObj != null) {
                    advanceToConflict = tryResolveObjective(quest, nextObj, ctx);
                }
                if (!advanceToConflict) {
                    LOGGER.atWarning().log(
                        "TURN_IN_V2: conflict %d objective unresolvable for quest %s " +
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
                    case FETCH_ITEM -> "hostile".equals(bindings.get("fetch_variant"))
                        ? "retrieve " + newObj.getTargetLabel() + " from " + bindings.getOrDefault("subject_name", "the area")
                        : "recover " + newObj.getTargetLabel();
                    case TALK_TO_NPC -> "speak with " + newObj.getTargetLabel();
                };
                bindings.put("quest_objective_summary", summary);
                ctx.systemLogger().accept("New objective: " + summary);

                saveQuest(questSystem, ctx.playerData(), quest);
                LOGGER.atInfo().log("TURN_IN_V2: quest %s advanced to conflict %d/%d",
                    quest.getQuestId(), quest.getConflictCount(), quest.getMaxConflicts());
            } else {
                quest.setState(com.chonbosmods.quest.QuestState.COMPLETED);
                ctx.dispositionUpdater().accept(QuestDispositionConstants.QUEST_COMPLETED);
                questSystem.getStateManager().markQuestCompleted(ctx.playerData(), quest.getQuestId());
                ctx.systemLogger().accept("Quest completed: " + quest.getSituationId());
                LOGGER.atInfo().log("TURN_IN_V2: quest %s completed", quest.getQuestId());
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

    /**
     * Validate and resolve an objective before advancing into it.
     * For TALK_TO_NPC with deferred target: try to find a real NPC now.
     * For KILL_MOBS/FETCH_ITEM: always resolvable (void or surface fallback).
     * For COLLECT_RESOURCES: always resolvable.
     * Returns true if the objective can be activated, false to skip to resolution.
     */
    private boolean tryResolveObjective(QuestInstance quest, ObjectiveInstance objective,
                                         ActionContext ctx) {
        ObjectiveType type = objective.getType();
        Map<String, String> bindings = quest.getVariableBindings();

        return switch (type) {
            case COLLECT_RESOURCES -> true;
            case KILL_MOBS, FETCH_ITEM -> true; // resolveAndPlacePoi handles void + surface fallback
            case TALK_TO_NPC -> {
                // If target was deferred at generation, try to resolve now
                if ("deferred_npc".equals(objective.getTargetId())) {
                    double npcX = 0, npcZ = 0;
                    try {
                        npcX = Double.parseDouble(bindings.getOrDefault("npc_x", "0"));
                        npcZ = Double.parseDouble(bindings.getOrDefault("npc_z", "0"));
                    } catch (NumberFormatException ignored) {}

                    SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
                    if (settlements == null) {
                        LOGGER.atWarning().log("tryResolveObjective: TALK_TO_NPC deferred but no settlement registry");
                        yield false;
                    }

                    // Find nearest other settlement with NPCs
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

                    if (nearest == null || nearest.getNpcs().isEmpty()) {
                        LOGGER.atWarning().log(
                            "tryResolveObjective: TALK_TO_NPC still unresolvable for quest %s: " +
                            "no other settlements with NPCs found near (%.0f, %.0f). " +
                            "Total settlements: %d",
                            quest.getQuestId(), npcX, npcZ, settlements.getAll().size());
                        yield false;
                    }

                    // Resolve: pick a random NPC from the nearest settlement.
                    // The target_npc trio (name, role, settlement) must always describe
                    // the same NPC and the same settlement, so authors can write
                    // "{target_npc}, the {target_npc_role} from {target_npc_settlement}".
                    NpcRecord targetNpc = nearest.getNpcs().get(
                        new Random(quest.getQuestId().hashCode()).nextInt(nearest.getNpcs().size()));
                    objective.setTargetId(targetNpc.getGeneratedName());
                    objective.setTargetLabel(targetNpc.getGeneratedName());
                    objective.setLocationId(nearest.getCellKey());
                    bindings.put("target_npc", targetNpc.getGeneratedName());
                    bindings.put("target_npc_role", targetNpc.getDisplayRole());
                    bindings.put("target_npc_settlement", nearest.deriveName());
                    bindings.put("target_npc_settlement_key", nearest.getCellKey());

                    LOGGER.atInfo().log("tryResolveObjective: TALK_TO_NPC resolved at runtime: " +
                        "target=%s in settlement %s for quest %s",
                        targetNpc.getGeneratedName(), nearest.getCellKey(), quest.getQuestId());
                }
                yield true;
            }
        };
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
        String targetSettlement = bindings.get("target_npc_settlement");
        String targetNpcName = bindings.get("target_npc");
        if (targetSettlement == null || targetNpcName == null) return;

        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null) return;

        SettlementRecord settlement = settlements.getByCell(targetSettlement);
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
