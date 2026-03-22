package com.chonbosmods.dialogue;

import com.chonbosmods.Natural20;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.model.DialogueGraph;
import com.chonbosmods.dialogue.model.LogEntry;
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

    public DialogueManager(DialogueLoader dialogueLoader, DialogueActionRegistry actionRegistry) {
        this.dialogueLoader = dialogueLoader;
        this.actionRegistry = actionRegistry;
        this.conditionEvaluator = new ConditionEvaluator();
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
            player.sendMessage(Message.raw("[Nat20] No dialogue found for: " +
                (npcData.getGeneratedName() != null ? npcData.getGeneratedName() : npcData.getRoleName())));
            return;
        }

        // Get or create player data
        Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (playerData == null) {
            playerData = store.addComponent(playerRef, Natural20.getPlayerDataType());
        }

        // Session cleanup callback
        Runnable onSessionEnd = () -> endSession(playerUuid);

        // Create presenter
        String displayName = npcData.getGeneratedName() != null ? npcData.getGeneratedName() : npcId;
        PageDialoguePresenter presenter = new PageDialoguePresenter(
                player, player.getPlayerRef(), playerRef, store, this, displayName);

        // Create session
        ConversationSession session = new ConversationSession(
                player, playerRef, npcRef,
                store, graph, playerData, npcData,
                actionRegistry, conditionEvaluator,
                presenter, onSessionEnd, onNpcRelease);

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
                        var logObj = el.getAsJsonObject();
                        String logType = logObj.get("type").getAsString();
                        LogEntry entry = switch (logType) {
                            case "TopicHeader" -> new LogEntry.TopicHeader(logObj.get("label").getAsString(), logObj.has("questTopic") && logObj.get("questTopic").getAsBoolean());
                            case "NpcSpeech" -> new LogEntry.NpcSpeech(logObj.get("text").getAsString());
                            case "SelectedResponse" -> new LogEntry.SelectedResponse(
                                    logObj.get("responseId").getAsString(),
                                    logObj.get("displayText").getAsString(),
                                    logObj.has("statPrefix") ? logObj.get("statPrefix").getAsString() : null);
                            case "SystemText" -> new LogEntry.SystemText(logObj.get("text").getAsString());
                            case "ReturnGreeting" -> new LogEntry.ReturnGreeting(logObj.get("text").getAsString());
                            case "ReturnDivider" -> new LogEntry.ReturnDivider();
                            default -> null;
                        };
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
            LOGGER.atInfo().log("Ended dialogue session for player %s", playerUuid);
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
}
