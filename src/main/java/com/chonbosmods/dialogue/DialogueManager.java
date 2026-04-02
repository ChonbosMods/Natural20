package com.chonbosmods.dialogue;

import com.chonbosmods.Natural20;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.quest.DialogueResolver;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.PhaseInstance;
import com.chonbosmods.quest.ObjectiveType;
import com.chonbosmods.quest.PhaseType;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.quest.QuestTemplateRegistry;
import com.chonbosmods.quest.model.DialogueChunks;
import com.chonbosmods.quest.model.QuestVariant;
import com.chonbosmods.topic.PostureResolver;
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
                npcId, 50, "fallback_greeting", null,
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

        // Inject turn-in topics for any quests this NPC gave that have completed objectives
        injectTurnInTopics(graph, npcId, playerData);
        // Inject talk-to-NPC topics for any quests targeting this NPC
        injectTalkToNpcTopics(graph, npcId, playerData);

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
     * Inject turn-in dialogue topics for quests that this NPC gave where
     * phase objectives are complete. Adds nodes and a priority topic directly
     * into the mutable graph so the conversation session picks them up.
     */
    private void injectTurnInTopics(DialogueGraph graph, String npcId, Nat20PlayerData playerData) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        QuestTemplateRegistry templateRegistry = questSystem.getTemplateRegistry();

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        for (QuestInstance quest : quests.values()) {
            if (!npcId.equals(quest.getSourceNpcId())) continue;
            if (!"true".equals(quest.getVariableBindings().get("phase_objectives_complete"))) continue;

            String questId = quest.getQuestId();
            Map<String, String> b = quest.getVariableBindings();
            PhaseInstance currentPhase = quest.getCurrentPhase();
            PhaseType phaseType = currentPhase != null ? currentPhase.getType() : PhaseType.EXPOSITION;
            boolean isFinalPhase = quest.getCurrentPhaseIndex() >= quest.getPhases().size() - 1;

            // Try to load variant dialogueChunks for richer narrative
            QuestVariant variant = null;
            if (currentPhase != null && currentPhase.getVariantId() != null) {
                variant = templateRegistry.getVariant(
                    quest.getSituationId(), phaseType, currentPhase.getVariantId());
            }
            DialogueChunks chunks = variant != null ? variant.dialogueChunks() : null;

            // Entry node text: variant intro or hardcoded fallback
            String turnInText = resolveChunkOrFallback(
                chunks != null ? chunks.intro() : null, b,
                buildTurnInText(phaseType, isFinalPhase, b));

            String topicLabel = "Report: " + b.getOrDefault("quest_title", quest.getSituationId());
            String topicId = "turnin_" + questId;
            String entryNodeId = topicId + "_entry";
            String actionNodeId = topicId + "_action";
            String plotNodeId = topicId + "_plot";
            String bridgeNodeId = topicId + "_bridge";
            String transitionNodeId = topicId + "_transition";

            // Build the post-action narrative from variant chunks
            // plotStep: narrative advancement after turn-in action
            String plotStepText = resolveChunkOrFallback(
                chunks != null ? chunks.plotStep() : null, b,
                isFinalPhase
                    ? "Your efforts won't be forgotten. Well done."
                    : "Good. " + buildNextPhaseBriefing(quest));

            // outro: transition to next phase (only for mid-quest)
            String outroText = null;
            if (!isFinalPhase && chunks != null && chunks.outro() != null && !chunks.outro().isBlank()) {
                outroText = DialogueResolver.resolve(chunks.outro(), b);
                if (outroText.isBlank()) outroText = null;
            }

            // Determine the node after the action node
            // If we have both plotStep and outro, chain: action -> plot -> bridge -> transition
            // If only plotStep (final phase or no outro), chain: action -> plot
            String afterActionNodeId;
            if (outroText != null) {
                // Chain: action -> plot -> bridge (no-op action) -> transition
                afterActionNodeId = plotNodeId;

                // Transition node: outro text + next-phase briefing (exhausts topic)
                String briefing = buildNextPhaseBriefing(quest);
                String transitionText = outroText + " " + briefing;

                graph.nodes().put(transitionNodeId, new DialogueNode.DialogueTextNode(
                    transitionText, null, List.of(), List.of(), true, false, null
                ));

                // Bridge node: no-op action that auto-advances from plot to transition
                graph.nodes().put(bridgeNodeId, new DialogueNode.ActionNode(
                    List.of(), transitionNodeId, List.of(), false
                ));

                // Plot node: plotStep text with a continue response leading to bridge
                graph.nodes().put(plotNodeId, new DialogueNode.DialogueTextNode(
                    plotStepText, null,
                    List.of(new ResponseOption(
                        topicId + "_continue", "[Continue]", null, bridgeNodeId,
                        ResponseMode.DECISIVE, null, null, null, null
                    )),
                    List.of(), false, false, null
                ));
            } else {
                // No outro: action goes straight to plot (which serves as confirm)
                afterActionNodeId = plotNodeId;

                // Append next-phase briefing to plotStep for mid-quest if using fallback
                String confirmText;
                if (isFinalPhase) {
                    confirmText = plotStepText;
                } else if (chunks == null) {
                    // Fallback path: plotStepText already contains "Good. " + briefing
                    confirmText = plotStepText;
                } else {
                    // Variant plotStep but no outro: append briefing
                    String briefing = buildNextPhaseBriefing(quest);
                    confirmText = plotStepText + " " + briefing;
                }

                graph.nodes().put(plotNodeId, new DialogueNode.DialogueTextNode(
                    confirmText, null, List.of(), List.of(), true, false, null
                ));
            }

            // Action node: TURN_IN_PHASE
            graph.nodes().put(actionNodeId, new DialogueNode.ActionNode(
                List.of(Map.of("type", "TURN_IN_PHASE", "questId", questId)),
                afterActionNodeId, List.of(), true
            ));

            // Entry node: NPC acknowledges return, player confirms turn-in
            graph.nodes().put(entryNodeId, new DialogueNode.DialogueTextNode(
                turnInText, null,
                List.of(new ResponseOption(
                    topicId + "_resp", "[Turn in] Yes, it's done.", null, actionNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                )),
                List.of(), false, false, null
            ));

            // Topic definition: priority (sortOrder -1), always visible, quest-flagged
            graph.topics().addFirst(new TopicDefinition(
                topicId, topicLabel, entryNodeId,
                TopicScope.LOCAL, null, true, null, -1, null, true
            ));

            LOGGER.atInfo().log("Injected turn-in topic for quest %s (phase: %s, final: %s, variant: %s)",
                questId, phaseType, isFinalPhase,
                variant != null ? variant.id() : "fallback");
        }
    }

    private static String buildNextPhaseBriefing(QuestInstance quest) {
        int nextIndex = quest.getCurrentPhaseIndex() + 1;
        if (nextIndex >= quest.getPhases().size()) return "We'll see what comes next.";

        PhaseInstance nextPhase = quest.getPhases().get(nextIndex);

        // Try to load the next variant's intro for narrative continuity
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem != null && nextPhase.getVariantId() != null) {
            QuestTemplateRegistry templateRegistry = questSystem.getTemplateRegistry();
            var nextVariant = templateRegistry.getVariant(
                quest.getSituationId(), nextPhase.getType(), nextPhase.getVariantId());
            if (nextVariant != null && nextVariant.dialogueChunks() != null
                    && nextVariant.dialogueChunks().intro() != null
                    && !nextVariant.dialogueChunks().intro().isBlank()) {
                return DialogueResolver.resolve(
                    nextVariant.dialogueChunks().intro(), quest.getVariableBindings());
            }
        }

        // Fallback to hardcoded objective-type text
        if (nextPhase.getObjectives().isEmpty()) return "There's still work ahead.";

        ObjectiveInstance obj = nextPhase.getObjectives().getFirst();
        String task = switch (obj.getType()) {
            case KILL_MOBS -> "You'll need to kill " + obj.getRequiredCount() + " " + obj.getTargetLabel() + ".";
            case COLLECT_RESOURCES -> "I need you to collect " + obj.getRequiredCount() + " " + obj.getTargetLabel() + ".";
            case FETCH_ITEM -> "I need you to find " + obj.getTargetLabel() + ".";
            case TALK_TO_NPC -> "Go speak with " + obj.getTargetLabel() + ".";
        };

        return switch (nextPhase.getType()) {
            case EXPOSITION -> "But first, I need more information. " + task;
            case CONFLICT -> "The real work starts now. " + task;
            case RESOLUTION -> "We're close to the end. " + task;
        };
    }

    private static String buildTurnInText(PhaseType phaseType, boolean isFinalPhase,
                                           Map<String, String> bindings) {
        String focus = bindings.getOrDefault("quest_focus", "the task");
        return switch (phaseType) {
            case EXPOSITION -> "You're back. Did you find out what you needed about " + focus + "?";
            case CONFLICT -> isFinalPhase
                ? "I can see it in your eyes: the threat is dealt with. Tell me everything."
                : "You've handled it, then? Good. But I don't think we're done yet.";
            case RESOLUTION -> "You've done it. I wasn't sure you would, but here you are. You've earned this.";
        };
    }

    private static String resolveChunkOrFallback(String chunkText, Map<String, String> bindings,
                                                  String fallback) {
        if (chunkText == null || chunkText.isBlank()) return fallback;
        String resolved = DialogueResolver.resolve(chunkText, bindings);
        return resolved.isBlank() ? fallback : resolved;
    }

    /**
     * If this NPC is the target of a TALK_TO_NPC objective, inject a quest dialogue topic
     * that, when selected, marks the objective complete and sets phase_objectives_complete.
     */
    private void injectTalkToNpcTopics(DialogueGraph graph, String npcId, Nat20PlayerData playerData) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        for (QuestInstance quest : quests.values()) {
            Map<String, String> b = quest.getVariableBindings();

            // Skip if objectives already complete (awaiting turn-in at quest giver)
            if ("true".equals(b.get("phase_objectives_complete"))) continue;

            PhaseInstance phase = quest.getCurrentPhase();
            if (phase == null) continue;

            for (ObjectiveInstance obj : phase.getObjectives()) {
                if (obj.getType() != ObjectiveType.TALK_TO_NPC) continue;
                if (obj.isComplete()) continue;

                // Match: targetId is the NPC's generated name
                if (!npcId.equals(obj.getTargetId())) continue;

                // This NPC is the target. Inject a quest dialogue topic.
                String questId = quest.getQuestId();
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
                    confirmText, null, List.of(), List.of(), true, false, null
                ));

                // Entry: target NPC delivers their dialogue
                String topicLabel = "About " + questTitle;
                graph.nodes().put(entryNodeId, new DialogueNode.DialogueTextNode(
                    targetDialogue, null,
                    List.of(new ResponseOption(
                        topicId + "_resp", "I'll pass that along.", null, actionNodeId,
                        ResponseMode.DECISIVE, null, null, null, null
                    )),
                    List.of(), false, false, null
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
}
