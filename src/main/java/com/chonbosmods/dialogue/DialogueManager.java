package com.chonbosmods.dialogue;

import com.chonbosmods.Natural20;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.model.DialogueGraph;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
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

    public void startSession(Ref<EntityStore> playerRef, Ref<EntityStore> npcRef, Store<EntityStore> store) {
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

        // Load dialogue graph for this NPC
        String npcId = npcData.getRoleName();
        DialogueGraph graph = dialogueLoader.getGraphForNpc(npcId);
        if (graph == null) {
            player.sendMessage(Message.raw("[Nat20] No dialogue found for role: " + npcId));
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
                presenter, onSessionEnd);

        activeSessions.put(playerUuid, session);

        // Check for saved session (dirty exit resume)
        String savedJson = playerData.getSavedSession(graph.npcId());
        if (savedJson != null) {
            // TODO: Deserialize saved session and call session.startFromSaved()
            playerData.clearSavedSession(graph.npcId());
            LOGGER.atInfo().log("Found saved session for %s with NPC '%s', resuming (deserialization TODO)", playerUuid, npcId);
        }

        LOGGER.atInfo().log("Started dialogue session for %s with NPC '%s' (role: %s)", playerUuid, displayName, npcId);
        session.start();
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
