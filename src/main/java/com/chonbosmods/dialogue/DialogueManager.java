package com.chonbosmods.dialogue;

import com.chonbosmods.Natural20;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.quest.DialogueResolver;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.ObjectiveType;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.chonbosmods.topic.PostureResolver;
import com.chonbosmods.ui.EntityHighlight;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DialogueManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|DialogueManager");

    /** Default disposition used for fallback dialogue graphs when no authored graph exists. */
    private static final int FALLBACK_DEFAULT_DISPOSITION = 50;

    private final DialogueLoader dialogueLoader;
    private final DialogueActionRegistry actionRegistry;
    private final ConditionEvaluator conditionEvaluator;
    private final Map<UUID, ConversationSession> activeSessions = new ConcurrentHashMap<>();
    private PostureResolver postureResolver;

    public DialogueManager(DialogueLoader dialogueLoader, DialogueActionRegistry actionRegistry) {
        this.dialogueLoader = dialogueLoader;
        this.actionRegistry = actionRegistry;
        this.conditionEvaluator = new ConditionEvaluator();
    }

    public void setPostureResolver(PostureResolver postureResolver) {
        this.postureResolver = postureResolver;
    }

    public void startSession(Ref<EntityStore> playerRef, Ref<EntityStore> npcRef, Store<EntityStore> store, Runnable onNpcRelease) {
        // Resolve Player from entity ref
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            LOGGER.atWarning().log("Could not resolve Player from entity ref");
            return;
        }

        UUID playerUuid = player.getPlayerRef().getUuid();

        // Ignore if player already in a session
        if (activeSessions.containsKey(playerUuid)) {
            LOGGER.atWarning().log("Player %s already in dialogue", playerUuid);
            return;
        }

        // Get NPC data
        Nat20NpcData npcData = store.getComponent(npcRef, Natural20.getNpcDataType());
        if (npcData == null) {
            LOGGER.atWarning().log("NPC has no Nat20NpcData component, cannot start dialogue");
            return;
        }

        // Load dialogue graph: try generated name first (for generated topics), then role name
        String npcId = npcData.getGeneratedName();
        DialogueGraph graph = npcId != null ? dialogueLoader.getGraphForNpc(npcId) : null;
        if (graph == null) {
            npcId = npcData.getRoleName();
            graph = dialogueLoader.getGraphForNpc(npcId);
        }
        if (graph == null) {
            // No authored or generated graph: create a minimal fallback so the
            // NPC is still interactable (quest topics may still be injected).
            String displayId = npcData.getGeneratedName() != null ? npcData.getGeneratedName() : npcData.getRoleName();
            LOGGER.atWarning().log("No dialogue graph for NPC '%s': using fallback", displayId);
            graph = new DialogueGraph(
                npcId, FALLBACK_DEFAULT_DISPOSITION, "fallback_greeting", null,
                new java.util.ArrayList<>(),
                new java.util.LinkedHashMap<>(java.util.Map.of(
                    "fallback_greeting", new DialogueNode.DialogueTextNode(
                        "...", null, java.util.List.of(), java.util.List.of(), false, false, null)
                ))
            );
        }

        // Get or create player data
        Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (playerData == null) {
            playerData = store.addComponent(playerRef, Natural20.getPlayerDataType());
        }

        // Defensive copy: quest injection mutates the graph, so each session
        // gets its own copy to prevent cross-session state bleed.
        graph = graph.mutableCopy();

        // Late-resolve any {tokens} that were unresolvable at generation time
        // (e.g., {other_settlement} when no neighbors existed yet).
        graph.lateResolve(buildLateBindings(npcData));

        // Inject quest topics: available quests (from NPC's preGeneratedQuest) and turn-in topics
        injectQuestAvailableTopics(graph, npcId, npcData);
        injectQuestTurnInTopics(graph, npcId, playerData);
        // Inject talk-to-NPC topics for any quests targeting this NPC
        injectTalkToNpcTopics(graph, npcId, playerData);

        // Clear exhaustion and consumed decisives for quest topics (rebuilt fresh each session).
        // Consumed decisives must be cleared so [Turn in] buttons work across conflict phases
        // (same topic ID reused for exposition and conflict turn-ins).
        for (TopicDefinition topic : graph.topics()) {
            if (!topic.questTopic()) continue;
            String tid = topic.id();
            if (tid.startsWith("questoffer_") || tid.startsWith("questturnin_") || tid.startsWith("talknpc_")) {
                playerData.removeTopicExhaustion(npcId, tid);
                playerData.clearConsumedDecisivesForTopic(npcId, tid);
            }
        }

        // Session cleanup callback
        Runnable onSessionEnd = () -> endSession(playerUuid);

        // Create presenter
        String displayName = npcData.getGeneratedName() != null ? npcData.getGeneratedName() : npcId;
        PageDialoguePresenter presenter = new PageDialoguePresenter(
                player, player.getPlayerRef(), playerRef, store, this, displayName,
                postureResolver);

        // Create session
        ConversationSession session = new ConversationSession(
                player, playerRef, npcRef,
                store, graph, playerData, npcData,
                actionRegistry, conditionEvaluator,
                presenter, onSessionEnd, onNpcRelease);

        // Evaluate valence drift for returning conversations
        String storedValence = playerData.getClosingValence(graph.npcId());
        if (storedValence != null) {
            ValenceType stored = ValenceType.fromString(storedValence);
            ValenceType opening = ValenceTracker.evaluateDrift(stored, new java.util.Random().nextDouble());
            session.seedValenceTracker(opening);
        }

        activeSessions.put(playerUuid, session);

        // Check for saved session (dirty exit resume)
        String savedJson = playerData.getSavedSession(graph.npcId());
        if (savedJson != null) {
            try {
                var savedData = JsonParser.parseString(savedJson).getAsJsonObject();

                // Deserialize log
                List<LogEntry> savedLog = new ArrayList<>();
                if (savedData.has("log")) {
                    for (var el : savedData.getAsJsonArray("log")) {
                        LogEntry entry = LogEntry.fromJson(el.getAsJsonObject());
                        if (entry != null) savedLog.add(entry);
                    }
                }

                String savedActiveNodeId = savedData.has("activeNodeId")
                        ? savedData.get("activeNodeId").getAsString() : null;
                String savedActiveTopicId = savedData.has("activeTopicId")
                        ? savedData.get("activeTopicId").getAsString() : null;
                List<String> savedPendingFollowUps = new ArrayList<>();
                if (savedData.has("pendingFollowUpIds")) {
                    for (var el : savedData.getAsJsonArray("pendingFollowUpIds")) {
                        savedPendingFollowUps.add(el.getAsString());
                    }
                }
                Set<String> savedGrayedExploratories = new HashSet<>();
                if (savedData.has("grayedExploratories")) {
                    for (var el : savedData.getAsJsonArray("grayedExploratories")) {
                        savedGrayedExploratories.add(el.getAsString());
                    }
                }

                playerData.clearSavedSession(graph.npcId());
                LOGGER.atInfo().log("Resuming saved session for %s with NPC '%s'", playerUuid, npcId);
                session.startFromSaved(savedLog, savedActiveNodeId, savedActiveTopicId,
                        savedPendingFollowUps, savedGrayedExploratories);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to restore saved session for %s, starting fresh", playerUuid);
                playerData.clearSavedSession(graph.npcId());
                session.start();
            }
        } else {
            LOGGER.atInfo().log("Started dialogue session for %s with NPC '%s' (role: %s)", playerUuid, displayName, npcId);
            session.start();
        }
    }

    public void handleTopicSelected(UUID playerUuid, String topicId) {
        ConversationSession session = activeSessions.get(playerUuid);
        if (session != null) {
            session.handleTopicSelected(topicId);
        }
    }

    public void handleFollowUpSelected(UUID playerUuid, String responseId) {
        ConversationSession session = activeSessions.get(playerUuid);
        if (session != null) {
            session.handleFollowUpSelected(responseId);
        }
    }

    public void endSession(UUID playerUuid) {
        ConversationSession session = activeSessions.remove(playerUuid);
        if (session != null && !session.isEnded()) {
            session.endDialogue();
        }
        if (session != null) {
            // Activate any pending quest objectives now that the dialogue session is over
            activatePendingObjectives(session.getPlayerData());
            LOGGER.atFine().log("Ended dialogue session for player %s", playerUuid);
        }
    }

    public boolean hasActiveSession(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    public ConversationSession getSession(UUID playerUuid) {
        return activeSessions.get(playerUuid);
    }

    public void endSessionForNpc(Ref<EntityStore> npcRef) {
        activeSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getNpcRef().equals(npcRef)) {
                entry.getValue().endDialogue();
                LOGGER.atInfo().log("Ended session due to NPC removal for player %s", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Build bindings for late-resolving {tokens} that were unresolvable at generation time.
     * Uses the current settlement registry so {other_settlement} etc. resolve to real data
     * even if the NPC's settlement was the first created.
     */
    /**
     * Flip any OBJECTIVE_PENDING quests to ACTIVE_OBJECTIVE now that the dialogue session ended.
     * This prevents tracking systems from auto-completing objectives while the player is still
     * in the dialogue that assigned them.
     */
    private void activatePendingObjectives(Nat20PlayerData playerData) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null || playerData == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        boolean changed = false;
        for (QuestInstance quest : quests.values()) {
            if (quest.getState() == com.chonbosmods.quest.QuestState.OBJECTIVE_PENDING) {
                quest.setState(com.chonbosmods.quest.QuestState.ACTIVE_OBJECTIVE);
                changed = true;
            }
        }
        if (changed) {
            questSystem.getStateManager().saveActiveQuests(playerData, quests);
        }
    }

    private static boolean hasPreGeneratedQuest(Nat20NpcData npcData) {
        if (npcData == null || npcData.getSettlementCellKey() == null) return false;
        var registry = Natural20.getInstance().getSettlementRegistry();
        if (registry == null) return false;
        var settlement = registry.getByCell(npcData.getSettlementCellKey());
        if (settlement == null) return false;
        var npcRecord = settlement.getNpcByName(npcData.getGeneratedName());
        return npcRecord != null && npcRecord.getPreGeneratedQuest() != null;
    }

    private Map<String, String> buildLateBindings(Nat20NpcData npcData) {
        Map<String, String> bindings = new HashMap<>();
        var registry = Natural20.getInstance().getSettlementRegistry();
        if (registry == null) return bindings;

        String cellKey = npcData.getSettlementCellKey();
        if (cellKey == null) return bindings;

        var settlement = registry.getByCell(cellKey);
        if (settlement == null) return bindings;

        // Other settlement names (the most common late-binding case)
        List<String> nearbyNames = new ArrayList<>();
        for (var other : registry.getAll().values()) {
            if (!other.getCellKey().equals(cellKey)) {
                nearbyNames.add(other.deriveName());
            }
        }
        if (!nearbyNames.isEmpty()) {
            var random = new Random(cellKey.hashCode());
            bindings.put("other_settlement",
                EntityHighlight.wrap(nearbyNames.get(random.nextInt(nearbyNames.size()))));
        }

        // POI and mob types from settlement type (in case they were empty at generation)
        var poiTypes = settlement.getSettlementType().getPoiTypes();
        if (!poiTypes.isEmpty()) {
            var random = new Random(cellKey.hashCode() ^ 7);
            bindings.put("poi_type", poiTypes.get(random.nextInt(poiTypes.size())));
        }
        var mobTypes = settlement.getSettlementType().getMobTypes();
        if (!mobTypes.isEmpty()) {
            var random = new Random(cellKey.hashCode() ^ 13);
            bindings.put("mob_type", mobTypes.get(random.nextInt(mobTypes.size())));
        }

        // Settlement name
        bindings.put("settlement_name", EntityHighlight.wrap(settlement.deriveName()));

        return bindings;
    }

    /**
     * Inject turn-in dialogue topics for quests that this NPC gave where
     * phase objectives are complete. Adds nodes and a priority topic directly
     * into the mutable graph so the conversation session picks them up.
     */
    /**
     * Inject quest available topic for NPCs with a preGeneratedQuest.
     * Builds the exposition -> accept/decline/skillcheck chain.
     */
    private void injectQuestAvailableTopics(DialogueGraph graph, String npcId, Nat20NpcData npcData) {
        if (!hasPreGeneratedQuest(npcData)) return;

        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        if (settlements == null || npcData.getSettlementCellKey() == null) return;
        SettlementRecord settlement = settlements.getByCell(npcData.getSettlementCellKey());
        if (settlement == null) return;
        NpcRecord npcRecord = settlement.getNpcByName(npcId);
        if (npcRecord == null || npcRecord.getPreGeneratedQuest() == null) return;

        QuestInstance quest = npcRecord.getPreGeneratedQuest();
        Map<String, String> b = quest.getVariableBindings();
        String questId = quest.getQuestId();

        String topicHeader = DialogueResolver.resolve(
            b.getOrDefault("quest_topic_header", quest.getSituationId()), b);
        String expositionText = DialogueResolver.resolve(
            b.getOrDefault("quest_exposition_text", "I need your help with something."), b);
        String acceptText = DialogueResolver.resolve(
            b.getOrDefault("quest_accept_text", "Thank you. Here's what I need."), b);
        String declineText = DialogueResolver.resolve(
            b.getOrDefault("quest_decline_text", "I understand. Perhaps another time."), b);

        String topicId = "questoffer_" + questId;
        String entryNodeId = topicId + "_expo";
        String acceptNodeId = topicId + "_accept";
        String declineNodeId = topicId + "_decline";

        // Accept node: shows accept text, fires GIVE_QUEST, exhausts topic (no continue button)
        graph.nodes().put(acceptNodeId, new DialogueNode.DialogueTextNode(
            acceptText, null, List.of(),
            List.of(Map.of("type", "GIVE_QUEST")),
            true, false, ValenceType.POSITIVE
        ));

        // Decline node: decline text, exhausts topic, locks conversation (force close)
        graph.nodes().put(declineNodeId, new DialogueNode.DialogueTextNode(
            declineText, null, List.of(), List.of(), true, true, ValenceType.NEGATIVE
        ));

        // Entry node: exposition text with ACCEPT/DECLINE options
        List<ResponseOption> responses = new ArrayList<>();
        responses.add(new ResponseOption(
            topicId + "_accept_opt", "ACCEPT", null, acceptNodeId,
            ResponseMode.DECISIVE, null, null, null, null
        ));
        responses.add(new ResponseOption(
            topicId + "_decline_opt", "DECLINE", null, declineNodeId,
            ResponseMode.DECISIVE, null, null, null, null
        ));
        // TODO: skill check option (deferred)

        graph.nodes().put(entryNodeId, new DialogueNode.DialogueTextNode(
            expositionText, null, responses, List.of(), false, false, ValenceType.NEUTRAL
        ));

        // Topic: priority sort, always visible, quest-flagged
        graph.topics().addFirst(new TopicDefinition(
            topicId, topicHeader, entryNodeId,
            TopicScope.LOCAL, null, true, null, -1, null, true
        ));

        LOGGER.atInfo().log("Injected quest available topic '%s' for quest %s on NPC %s",
            topicHeader, questId, npcId);
    }

    /**
     * Inject turn-in topic for quests in READY_FOR_TURN_IN state.
     * Builds: turn-in text -> [Turn in] -> TURN_IN_V2 action -> conflict text or resolution text.
     */
    private void injectQuestTurnInTopics(DialogueGraph graph, String npcId, Nat20PlayerData playerData) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        for (QuestInstance quest : quests.values()) {
            if (!npcId.equals(quest.getSourceNpcId())) continue;
            if (quest.getState() != com.chonbosmods.quest.QuestState.READY_FOR_TURN_IN) continue;

            String questId = quest.getQuestId();
            Map<String, String> b = quest.getVariableBindings();
            int cc = quest.getConflictCount();

            // Select turn-in text based on conflict count
            String turnInTextKey = switch (cc) {
                case 0 -> "quest_exposition_turnin_text";
                case 1 -> "quest_conflict1_turnin_text";
                default -> "quest_conflict2_turnin_text";
            };
            String turnInText = DialogueResolver.resolve(
                b.getOrDefault(turnInTextKey, "You're back. Tell me what happened."), b);

            // Conflict text (for the NEXT conflict, if triggered)
            String conflictTextKey = switch (cc) {
                case 0 -> "quest_conflict1_text";
                default -> "quest_conflict2_text";
            };
            String conflictText = DialogueResolver.resolve(
                b.getOrDefault(conflictTextKey, ""), b);

            String resolutionText = DialogueResolver.resolve(
                b.getOrDefault("quest_resolution_text", "Thank you. You've done well."), b);

            String topicHeader = DialogueResolver.resolve(
                b.getOrDefault("quest_topic_header", quest.getSituationId()), b);

            String topicId = "questturnin_" + questId + "_c" + cc;
            String entryNodeId = topicId + "_entry";
            String actionNodeId = topicId + "_action";
            String conflictNodeId = topicId + "_conflict";
            String resolutionNodeId = topicId + "_resolution";

            // Resolution node: quest complete, exhausts topic
            graph.nodes().put(resolutionNodeId, new DialogueNode.DialogueTextNode(
                resolutionText, null, List.of(), List.of(), true, false, ValenceType.POSITIVE
            ));

            // Conflict node: new objective text, exhausts topic (player goes to do objective)
            if (!conflictText.isEmpty()) {
                graph.nodes().put(conflictNodeId, new DialogueNode.DialogueTextNode(
                    conflictText, null, List.of(), List.of(), true, false, ValenceType.NEGATIVE
                ));
            }

            // TURN_IN_V2 action: next node is deterministic (decided at quest generation)
            String afterTurnIn = quest.hasMoreConflicts() ? conflictNodeId : resolutionNodeId;
            graph.nodes().put(actionNodeId, new DialogueNode.ActionNode(
                List.of(Map.of("type", "TURN_IN_V2", "questId", questId)),
                afterTurnIn, List.of(), true
            ));

            // Entry node: turn-in text with [Turn in] button
            graph.nodes().put(entryNodeId, new DialogueNode.DialogueTextNode(
                turnInText, null,
                List.of(new ResponseOption(
                    topicId + "_turnin_resp", "[Turn in]", null, actionNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                )),
                List.of(), false, false, ValenceType.NEUTRAL
            ));

            // Topic: same header as the quest, priority sort, quest-flagged
            graph.topics().addFirst(new TopicDefinition(
                topicId, topicHeader, entryNodeId,
                TopicScope.LOCAL, null, true, null, -1, null, true
            ));

            LOGGER.atInfo().log("Injected turn-in topic for quest %s (conflict %d) on NPC %s",
                questId, cc, npcId);
        }
    }

    /**
     * If this NPC is the target of a TALK_TO_NPC objective, inject a quest dialogue topic
     * that, when selected, marks the objective complete.
     */
    private void injectTalkToNpcTopics(DialogueGraph graph, String npcId, Nat20PlayerData playerData) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        for (QuestInstance quest : quests.values()) {
            if (quest.getState() != com.chonbosmods.quest.QuestState.ACTIVE_OBJECTIVE) continue;

            ObjectiveInstance obj = quest.getCurrentObjective();
            if (obj == null || obj.getType() != ObjectiveType.TALK_TO_NPC || obj.isComplete()) continue;

            // Match: targetId is the NPC's generated name
            if (!npcId.equals(obj.getTargetId())) continue;

                // This NPC is the target. Inject a quest dialogue topic.
                String questId = quest.getQuestId();
                Map<String, String> b = quest.getVariableBindings();
                String targetDialogue = b.getOrDefault("target_npc_dialogue",
                    "You're looking into the situation? I can tell you what I know.");
                String questTitle = b.getOrDefault("quest_title", quest.getSituationId());
                String questGiver = quest.getSourceNpcId();

                String topicId = "talknpc_" + questId;
                String entryNodeId = topicId + "_entry";
                String actionNodeId = topicId + "_action";
                String confirmNodeId = topicId + "_confirm";

                // Action: COMPLETE_TALK_TO_NPC
                graph.nodes().put(actionNodeId, new DialogueNode.ActionNode(
                    List.of(Map.of("type", "COMPLETE_TALK_TO_NPC", "questId", questId)),
                    confirmNodeId, List.of(), true
                ));

                // Confirm: direct player back to quest giver
                String confirmText = "Tell " + questGiver + " what I've told you. They'll want to hear it.";
                graph.nodes().put(confirmNodeId, new DialogueNode.DialogueTextNode(
                    confirmText, null, List.of(), List.of(), true, false, ValenceType.NEUTRAL
                ));

                // Entry: target NPC delivers their dialogue
                String topicLabel = "About " + questTitle;
                graph.nodes().put(entryNodeId, new DialogueNode.DialogueTextNode(
                    targetDialogue, null,
                    List.of(new ResponseOption(
                        topicId + "_resp", "I'll pass that along.", null, actionNodeId,
                        ResponseMode.DECISIVE, null, null, null, null
                    )),
                    List.of(), false, false, ValenceType.NEUTRAL
                ));

                // Topic: priority sort, always visible, quest-flagged
                graph.topics().addFirst(new TopicDefinition(
                    topicId, topicLabel, entryNodeId,
                    TopicScope.LOCAL, null, true, null, 0, null, true
                ));

            LOGGER.atInfo().log("Injected TALK_TO_NPC topic for quest %s on NPC %s",
                questId, npcId);
        }
    }
}
