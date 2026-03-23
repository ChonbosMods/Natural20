package com.chonbosmods.action;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.quest.POIPopulationListener;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.quest.QuestInstance;
import com.hypixel.hytale.component.Ref;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DialogueActionRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private final Map<String, DialogueAction> actions = new HashMap<>();

    public DialogueActionRegistry() {
        register("SET_FLAG", (ctx, params) -> {
            String flagId = params.get("flagId");
            String value = params.getOrDefault("value", "true");
            ctx.playerData().getGlobalFlags().put(flagId, value);
        });

        register("MODIFY_DISPOSITION", (ctx, params) -> {
            int amount = Integer.parseInt(params.getOrDefault("amount", "0"));
            ctx.dispositionUpdater().accept(amount);
        });

        register("GIVE_ITEM", (ctx, params) -> {
            String itemId = params.get("itemId");
            int quantity = Integer.parseInt(params.getOrDefault("quantity", "1"));
            LOGGER.atInfo().log("GIVE_ITEM stub: %s x%d to %s", itemId, quantity, ctx.player().getPlayerRef().getUuid());
        });

        register("REMOVE_ITEM", (ctx, params) -> {
            String itemId = params.get("itemId");
            int quantity = Integer.parseInt(params.getOrDefault("quantity", "1"));
            LOGGER.atInfo().log("REMOVE_ITEM stub: %s x%d from %s", itemId, quantity, ctx.player().getPlayerRef().getUuid());
        });

        register("UNLOCK_TOPIC", (ctx, params) -> {
            String topicId = params.get("topicId");
            String scope = params.getOrDefault("scope", "LOCAL");
            if ("GLOBAL".equals(scope)) {
                ctx.globalTopicUnlocker().accept(topicId);
                LOGGER.atInfo().log("UNLOCK_TOPIC: player %s learned global topic '%s' (via NPC %s)",
                    ctx.player().getPlayerRef().getUuid(), topicId, ctx.npcId());
            }
        });

        register("EXECUTE_COMMAND", (ctx, params) -> {
            String command = params.getOrDefault("command", "");
            LOGGER.atInfo().log("EXECUTE_COMMAND stub: %s", command);
        });

        register("GIVE_QUEST", (ctx, params) -> {
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null) {
                LOGGER.atWarning().log("GIVE_QUEST: quest system not initialized");
                return;
            }
            String npcRole = ctx.npcData() != null ? ctx.npcData().getRoleName() : "Villager";
            String npcId = ctx.npcId();
            String settlementCellKey = ctx.npcData() != null ? ctx.npcData().getSettlementCellKey() : "";

            double npcX = 0, npcZ = 0;
            try {
                var transform = ctx.store().getComponent(ctx.npcRef(),
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
                if (transform != null) {
                    var pos = transform.getPosition();
                    npcX = pos.getX();
                    npcZ = pos.getZ();
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("GIVE_QUEST: could not get NPC position");
            }

            Set<String> completed = questSystem.getStateManager().getCompletedQuestIds(ctx.playerData());
            QuestInstance quest = questSystem.getGenerator().generate(
                npcRole, npcId, settlementCellKey, npcX, npcZ, completed);
            if (quest != null) {
                questSystem.getStateManager().addQuest(ctx.playerData(), quest);

                // If quest has a POI, claim the void and place the structure
                if ("true".equals(quest.getVariableBindings().get("poi_available"))) {
                    // Parse population spec before placement so we can spawn after prefab is pasted
                    String popSpec = quest.getVariableBindings().get("poi_population_spec");
                    String mobRole = null;
                    int mobCount = 0;
                    if (popSpec != null && popSpec.startsWith("KILL_MOBS:")) {
                        int lastColon = popSpec.lastIndexOf(':');
                        mobCount = Integer.parseInt(popSpec.substring(lastColon + 1));
                        mobRole = popSpec.substring("KILL_MOBS:".length(), lastColon);
                    }
                    triggerPOIPlacement(quest, ctx.store(), ctx.playerRef(), mobRole, mobCount);
                }

                ctx.systemLogger().accept("Quest accepted: " + quest.getSituationId());
                LOGGER.atInfo().log("GIVE_QUEST: player %s received quest '%s' (situation=%s, phases=%d) from NPC %s",
                    ctx.player().getPlayerRef().getUuid(), quest.getQuestId(), quest.getSituationId(),
                    quest.getPhases().size(), npcId);

                for (var phase : quest.getPhases()) {
                    if (phase.getReferenceId() != null) {
                        questSystem.getReferenceManager().injectReference(
                            ctx.playerData(), quest.getSituationId(),
                            phase.getReferenceId(), npcX, npcZ);
                    }
                }
            } else {
                LOGGER.atWarning().log("GIVE_QUEST: quest generation returned null for NPC %s (role=%s)", npcId, npcRole);
            }
        });

        register("COMPLETE_QUEST", (ctx, params) -> {
            String questId = params.get("questId");
            QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
            if (questSystem == null || questId == null) return;

            QuestInstance quest = questSystem.getStateManager().getQuest(ctx.playerData(), questId);
            if (quest != null && quest.isComplete()) {
                questSystem.getStateManager().markQuestCompleted(ctx.playerData(), questId);
                questSystem.getReferenceManager().cleanupQuestReferences(ctx.playerData(), quest);
                ctx.player().sendMessage(Message.raw("Quest completed: " + quest.getSituationId()));
            }
        });

        register("OPEN_SHOP", (ctx, params) -> {
            LOGGER.atInfo().log("OPEN_SHOP stub for %s", ctx.player().getPlayerRef().getUuid());
        });

        register("CHANGE_REPUTATION", (ctx, params) -> {
            String factionId = params.get("factionId");
            int amount = Integer.parseInt(params.getOrDefault("amount", "0"));
            var rep = ctx.playerData().getReputation();
            rep.put(factionId, rep.getOrDefault(factionId, 0) + amount);
        });

        register("EXHAUST_TOPIC", (ctx, params) -> {
            String topicId = params.get("topicId");
            ctx.topicExhauster().accept(topicId);
        });

        register("REACTIVATE_TOPIC", (ctx, params) -> {
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

                    // Spawn mobs immediately: chunks are guaranteed loaded after prefab paste
                    if (mobRole != null && mobCount > 0) {
                        Natural20.getInstance().getPOIPopulationListener().populateNow(
                            world, quest, playerRef, entrance.getX(), entrance.getY(), entrance.getZ(),
                            mobRole, mobCount);
                    }
                });
                LOGGER.atInfo().log("POI placed for quest %s at (%d, %d, %d)",
                    quest.getQuestId(), entrance.getX(), entrance.getY(), entrance.getZ());
            });
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
