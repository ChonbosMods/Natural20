package com.chonbosmods.dialogue;

import com.chonbosmods.action.ActionContext;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.dialogue.model.ActiveFollowUp;
import com.chonbosmods.dice.SkillCheckResult;
import com.chonbosmods.stats.PlayerStats;
import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;

public class ConversationSession {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final int MAX_ACTION_CHAIN = 10;

    // Identity
    private final String npcId;
    private final UUID playerId;
    private final Player player;
    private final Ref<EntityStore> playerRef;
    private final Ref<EntityStore> npcRef;
    private final Store<EntityStore> store;

    // Data
    private final DialogueGraph graph;
    private final Nat20PlayerData playerData;
    private final Nat20NpcData npcData;
    private final DialogueActionRegistry actionRegistry;
    private final ConditionEvaluator conditionEvaluator;

    // State
    private final List<LogEntry> conversationLog = new ArrayList<>();
    private List<ActiveFollowUp> activeFollowUps = new ArrayList<>();
    private String activeNodeId;
    private final List<String> pendingFollowUpIds = new ArrayList<>();
    private final Set<String> grayedExploratories = new HashSet<>();
    private boolean exhaustTopicFired;
    private int disposition;
    private boolean topicsLocked;
    private String activeTopicId;
    private boolean ended;

    // Deferred deepener resolution
    private final DeferredDeepenerResolver deepenerResolver;

    // Callbacks
    private final DialoguePresenter presenter;
    private final Runnable onSessionEnd;
    private final Runnable onNpcRelease;

    public ConversationSession(
            Player player,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store,
            DialogueGraph graph,
            Nat20PlayerData playerData,
            Nat20NpcData npcData,
            DialogueActionRegistry actionRegistry,
            ConditionEvaluator conditionEvaluator,
            DeferredDeepenerResolver deepenerResolver,
            DialoguePresenter presenter,
            Runnable onSessionEnd,
            Runnable onNpcRelease
    ) {
        this.player = player;
        this.playerId = player.getPlayerRef().getUuid();
        this.npcId = graph.npcId();
        this.playerRef = playerRef;
        this.npcRef = npcRef;
        this.store = store;
        this.graph = graph;
        this.playerData = playerData;
        this.npcData = npcData;
        this.actionRegistry = actionRegistry;
        this.conditionEvaluator = conditionEvaluator;
        this.deepenerResolver = deepenerResolver;
        this.presenter = presenter;
        this.onSessionEnd = onSessionEnd;
        this.onNpcRelease = onNpcRelease;
        int defaultDisp = npcData.getDefaultDisposition() != 0
            ? npcData.getDefaultDisposition()
            : graph.defaultDisposition();
        this.disposition = playerData.getDispositionFor(npcId, defaultDisp);
    }

    // --- Lifecycle ---

    public void start() {
        processNode(graph.greetingNodeId());
        presenter.openInitialPage(resolveVisibleTopics(), disposition);
    }

    public void startFromSaved(List<LogEntry> savedLog, String savedActiveNodeId,
                                String savedActiveTopicId,
                                List<String> savedPendingFollowUps,
                                Set<String> savedGrayedExploratories) {
        conversationLog.addAll(savedLog);
        grayedExploratories.addAll(savedGrayedExploratories);
        activeTopicId = savedActiveTopicId;

        if (!savedPendingFollowUps.isEmpty()) {
            pendingFollowUpIds.addAll(savedPendingFollowUps);
            activeNodeId = savedActiveNodeId;

            DialogueNode node = graph.getNode(savedActiveNodeId);
            if (node instanceof DialogueNode.DialogueTextNode textNode) {
                topicsLocked = textNode.locksConversation();
                Set<String> pendingSet = new HashSet<>(pendingFollowUpIds);
                for (ResponseOption opt : textNode.responses()) {
                    if (pendingSet.contains(opt.id())) {
                        activeFollowUps.add(new ActiveFollowUp(
                            opt.id(), opt.displayText(), null, opt.statPrefix(), false));
                    } else if (opt.mode() == ResponseMode.EXPLORATORY
                            && savedGrayedExploratories.contains(opt.id())) {
                        activeFollowUps.add(new ActiveFollowUp(
                            opt.id(), opt.displayText(), null, opt.statPrefix(), true));
                    }
                }
            }
        }

        presenter.refreshLog(conversationLog);
        presenter.refreshFollowUps(activeFollowUps);
        presenter.openInitialPage(resolveVisibleTopics(), disposition);
    }

    public void endDialogue() {
        if (ended) return;
        ended = true;

        playerData.setDispositionFor(npcId, disposition);

        if (!pendingFollowUpIds.isEmpty()) {
            saveSession();
        } else {
            playerData.clearSavedSession(npcId);
        }

        presenter.close();
        if (onNpcRelease != null) {
            onNpcRelease.run();
        }
        onSessionEnd.run();
    }

    // --- Topic Selection ---

    public void handleTopicSelected(String topicId) {
        if (topicsLocked || ended) return;

        TopicDefinition topic = graph.topics().stream()
            .filter(t -> t.id().equals(topicId))
            .findFirst().orElse(null);
        if (topic == null) return;

        // Exhausted topic: show contextual recap
        ExhaustionState exhaustionState = playerData.getTopicExhaustionState(npcId, topicId);
        if (exhaustionState == ExhaustionState.GRAYED) {
            Set<String> consumed = playerData.getConsumedDecisivesFor(npcId, topicId);
            String entryNodeId = resolveEntryNodeId(topic);

            if (!consumed.isEmpty()) {
                // Decisive path: show NPC response from the last decisive choice
                String recapNodeId = playerData.getTopicRecapNode(npcId, topicId);
                DialogueNode recapNode = recapNodeId != null ? graph.getNode(recapNodeId) : null;
                String text;
                if (recapNode instanceof DialogueNode.DialogueTextNode recapText) {
                    text = recapText.speakerText();
                } else {
                    // Fallback: recapText or entry node speech
                    DialogueNode entryNode = graph.getNode(entryNodeId);
                    text = topic.recapText() != null ? topic.recapText()
                        : (entryNode instanceof DialogueNode.DialogueTextNode tn ? tn.speakerText() : null);
                }
                if (text != null) {
                    text = deepenerResolver.resolve(text);
                    conversationLog.add(new LogEntry.TopicHeader(topic.label(), topic.questTopic()));
                    conversationLog.add(new LogEntry.NpcSpeech(text));
                }
                // Show surviving follow-ups (grayed exploratories) if any exist
                DialogueNode entryNode = graph.getNode(entryNodeId);
                if (entryNode instanceof DialogueNode.DialogueTextNode textNode) {
                    activeTopicId = topicId;
                    activeNodeId = entryNodeId;
                    filterAndDisplayResponses(textNode);
                }
                presenter.refreshLog(conversationLog);
                presenter.refreshFollowUps(activeFollowUps);
                presenter.flushUpdates();
            } else {
                // Pure exploratory exhaustion: show recap text, no follow-ups
                String recapNodeId = playerData.getTopicRecapNode(npcId, topicId);
                DialogueNode recapNode = recapNodeId != null ? graph.getNode(recapNodeId) : null;
                String text;
                if (recapNode instanceof DialogueNode.DialogueTextNode recapText) {
                    text = recapText.speakerText();
                } else {
                    DialogueNode entryNode = graph.getNode(entryNodeId);
                    text = topic.recapText() != null ? topic.recapText()
                        : (entryNode instanceof DialogueNode.DialogueTextNode tn ? tn.speakerText() : null);
                }
                if (text != null) {
                    text = deepenerResolver.resolve(text);
                    conversationLog.add(new LogEntry.TopicHeader(topic.label(), topic.questTopic()));
                    conversationLog.add(new LogEntry.NpcSpeech(text));
                }
                activeFollowUps = List.of();
                pendingFollowUpIds.clear();
                presenter.refreshLog(conversationLog);
                presenter.refreshFollowUps(activeFollowUps);
                presenter.flushUpdates();
            }
            return;
        }

        // Normal topic selection: clear stale follow-ups from previous topic
        activeFollowUps = new ArrayList<>();
        pendingFollowUpIds.clear();
        topicsLocked = false;
        exhaustTopicFired = false;
        activeTopicId = topicId;
        conversationLog.add(new LogEntry.TopicHeader(topic.label(), topic.questTopic()));
        presenter.refreshLog(conversationLog);
        presenter.refreshFollowUps(activeFollowUps);

        String entryNodeId = resolveEntryNodeId(topic);
        processNode(entryNodeId);
        presenter.refreshTopics(resolveVisibleTopics());
        presenter.flushUpdates();
    }

    // --- Follow-Up Selection ---

    public void handleFollowUpSelected(String responseId) {
        if (ended) return;

        DialogueNode node = graph.getNode(activeNodeId);
        if (!(node instanceof DialogueNode.DialogueTextNode textNode)) return;

        ResponseOption selected = textNode.responses().stream()
            .filter(r -> r.id().equals(responseId))
            .findFirst().orElse(null);
        if (selected == null) return;

        markFollowUpSelected(responseId, selected);

        if (selected.skillCheckRef() != null) {
            processNode(selected.skillCheckRef());
            presenter.flushUpdates();
            return;
        }

        processNode(selected.targetNodeId());
        presenter.refreshTopics(resolveVisibleTopics());
        presenter.flushUpdates();
    }

    // --- Node Processing ---

    private void processNode(String nodeId) {
        processNode(nodeId, 0);
    }

    private void processNode(String nodeId, int actionDepth) {
        if (nodeId == null || ended) return;
        if (actionDepth > MAX_ACTION_CHAIN) {
            LOGGER.atSevere().log("Action chain depth exceeded at node: %s", nodeId);
            return;
        }

        activeNodeId = nodeId;
        DialogueNode node = graph.getNode(nodeId);
        if (node == null) {
            LOGGER.atSevere().log("Missing node: %s", nodeId);
            return;
        }

        executeActions(node.onEnter());

        switch (node) {
            case DialogueNode.DialogueTextNode textNode -> {
                String displayText;
                if (textNode.reactionPool() != null && !textNode.reactionPool().isEmpty()) {
                    // V2: pick a random reaction from the pool each session visit
                    displayText = textNode.reactionPool().get(
                        new java.util.Random().nextInt(textNode.reactionPool().size()));
                } else {
                    // V1/normal: use speakerText with deferred deepener resolution
                    displayText = deepenerResolver.resolve(textNode.speakerText());
                }
                conversationLog.add(new LogEntry.NpcSpeech(displayText));
                filterAndDisplayResponses(textNode);

                if (textNode.exhaustsTopic() && activeTopicId != null) {
                    playerData.setTopicExhaustion(npcId, activeTopicId, ExhaustionState.HIDDEN);
                    exhaustTopicFired = true;
                }

                topicsLocked = textNode.locksConversation();

                if (pendingFollowUpIds.isEmpty()) {
                    returnCheck();
                }

                presenter.refreshLog(conversationLog);
                presenter.refreshFollowUps(activeFollowUps);
                presenter.refreshDisposition(disposition);
            }

            case DialogueNode.SkillCheckNode checkNode -> {
                int effectiveDC = DispositionBracket.effectiveDC(
                    checkNode.baseDC(), disposition, checkNode.dispositionScaling());
                PlayerStats stats = PlayerStats.from(playerData);
                presenter.showSkillCheck(checkNode, effectiveDC, stats);
            }

            case DialogueNode.ActionNode actionNode -> {
                executeActions(actionNode.actions());
                if (actionNode.exhaustsTopic() && activeTopicId != null) {
                    playerData.setTopicExhaustion(npcId, activeTopicId, ExhaustionState.HIDDEN);
                    exhaustTopicFired = true;
                }
                processNode(actionNode.nextNodeId(), actionDepth + 1);
            }

            case DialogueNode.TerminalNode terminalNode -> {
                if (terminalNode.exhaustsTopic() && activeTopicId != null) {
                    playerData.setTopicExhaustion(npcId, activeTopicId, ExhaustionState.HIDDEN);
                    exhaustTopicFired = true;
                }
                returnCheck();
            }
        }
    }

    // --- Skill Check Completion ---

    public void handleSkillCheckResult(SkillCheckResult result, DialogueNode.SkillCheckNode checkNode) {
        // Emit skill check result to dialogue log
        com.chonbosmods.stats.Skill skill = checkNode.skill();
        com.chonbosmods.stats.Stat stat = skill.getStat();

        conversationLog.add(new LogEntry.SkillCheckResult(
            stat.name(), skill.displayName(), result.totalRoll(),
            result.passed(), result.critical()
        ));

        String nextNodeId = result.passed() ? checkNode.passNodeId() : checkNode.failNodeId();
        processNode(nextNodeId);
        presenter.refreshTopics(resolveVisibleTopics());
        presenter.flushUpdates();
    }

    // --- Internal Helpers ---

    private void filterAndDisplayResponses(DialogueNode.DialogueTextNode textNode) {
        Set<String> consumed = (activeTopicId != null)
            ? playerData.getConsumedDecisivesFor(npcId, activeTopicId)
            : Set.of();

        ConditionContext condCtx = buildConditionContext();

        List<ResponseOption> surviving = textNode.responses().stream()
            .filter(r -> !consumed.contains(r.id()))
            .filter(r -> conditionEvaluator.evaluate(r.condition(), condCtx))
            .toList();

        pendingFollowUpIds.clear();
        activeFollowUps = new ArrayList<>();

        for (ResponseOption opt : surviving) {
            boolean grayed = opt.mode() == ResponseMode.EXPLORATORY
                    && grayedExploratories.contains(opt.id());
            if (!grayed) {
                pendingFollowUpIds.add(opt.id());
            }
            activeFollowUps.add(new ActiveFollowUp(
                    opt.id(), opt.displayText(), opt.logText(), opt.statPrefix(), grayed));
        }

    }

    private void markFollowUpSelected(String selectedId, ResponseOption selected) {
        // Record the selection in the conversation log (history only)
        String logDisplay = selected.logText() != null ? selected.logText() : selected.displayText();
        conversationLog.add(new LogEntry.SelectedResponse(selectedId, logDisplay, selected.statPrefix()));

        if (selected.mode() == ResponseMode.EXPLORATORY) {
            grayedExploratories.add(selectedId);
        } else {
            Set<String> toConsume = new HashSet<>();
            toConsume.add(selectedId);
            if (selected.linkedResponses() != null) {
                toConsume.addAll(selected.linkedResponses());
            }
            if (activeTopicId != null) {
                for (String id : toConsume) {
                    playerData.addConsumedDecisive(npcId, activeTopicId, id);
                }
            }
        }

        // Clear active follow-ups: we're navigating away
        activeFollowUps = List.of();
        pendingFollowUpIds.clear();
    }

    private void returnCheck() {
        if (activeTopicId == null) return;

        // Step 1: Was EXHAUST_TOPIC fired?
        if (exhaustTopicFired) {
            activeFollowUps = List.of();
            pendingFollowUpIds.clear();
            activeTopicId = null;
            topicsLocked = false;
            presenter.refreshLog(conversationLog);
            presenter.refreshFollowUps(activeFollowUps);
            presenter.refreshTopics(resolveVisibleTopics());
            presenter.refreshDisposition(disposition);
            // Note: no flushUpdates() here - caller (handleTopicSelected etc.) flushes after processNode
            return;
        }

        // Step 2: Evaluate entry node's remaining responses
        TopicDefinition topic = graph.topics().stream()
            .filter(t -> t.id().equals(activeTopicId))
            .findFirst().orElse(null);
        if (topic == null) return;

        String entryNodeId = resolveEntryNodeId(topic);
        DialogueNode entryNode = graph.getNode(entryNodeId);
        if (!(entryNode instanceof DialogueNode.DialogueTextNode textNode)) {
            // Entry node is a skill check or other non-dialogue node: auto-exhaust
            playerData.setTopicExhaustion(npcId, activeTopicId, ExhaustionState.GRAYED);
            topicsLocked = false;
            return;
        }

        Set<String> consumed = playerData.getConsumedDecisivesFor(npcId, activeTopicId);
        ConditionContext condCtx = buildConditionContext();

        List<ResponseOption> surviving = textNode.responses().stream()
            .filter(r -> !consumed.contains(r.id()))
            .filter(r -> conditionEvaluator.evaluate(r.condition(), condCtx))
            .toList();

        boolean hasFreshExploratories = surviving.stream()
            .anyMatch(r -> r.mode() == ResponseMode.EXPLORATORY && !grayedExploratories.contains(r.id()));
        boolean hasRemainingDecisives = surviving.stream()
            .anyMatch(r -> r.mode() == ResponseMode.DECISIVE);

        // Step 3: Return to entry or auto-exhaust
        if (hasFreshExploratories || hasRemainingDecisives) {
            activeNodeId = entryNodeId;
            redisplayEntryNodeOptions(textNode);
            topicsLocked = textNode.locksConversation();
        } else {
            playerData.setTopicExhaustion(npcId, activeTopicId, ExhaustionState.GRAYED);
            playerData.setTopicRecapNode(npcId, activeTopicId, activeNodeId);
            activeFollowUps = List.of();
            pendingFollowUpIds.clear();
            activeTopicId = null;
            topicsLocked = false;
        }

        presenter.refreshLog(conversationLog);
        presenter.refreshFollowUps(activeFollowUps);
        presenter.refreshTopics(resolveVisibleTopics());
        presenter.refreshDisposition(disposition);
    }

    private void redisplayEntryNodeOptions(DialogueNode.DialogueTextNode entryTextNode) {
        conversationLog.add(new LogEntry.ReturnDivider());
        filterAndDisplayResponses(entryTextNode);
    }

    private String resolveEntryNodeId(TopicDefinition topic) {
        String override = playerData.getTopicEntryOverride(npcId, topic.id());
        if (override != null) {
            if (graph.getNode(override) != null) {
                return override;
            }
            LOGGER.atWarning().log("REACTIVATE_TOPIC: newEntryNodeId '%s' not found in graph for topic '%s', falling back to default", override, topic.id());
            var npcOverrides = playerData.getTopicEntryOverrides().get(npcId);
            if (npcOverrides != null) npcOverrides.remove(topic.id());
        }
        return topic.entryNodeId();
    }

    private void executeActions(List<Map<String, String>> actions) {
        if (actions == null || actions.isEmpty()) return;
        ActionContext ctx = buildActionContext();
        for (Map<String, String> actionData : actions) {
            String type = actionData.get("type");
            if (type != null) {
                actionRegistry.execute(type, ctx, actionData);
            }
        }
    }

    private ActionContext buildActionContext() {
        return new ActionContext(
            player, playerRef, npcRef, store, npcId,
            playerData, npcData,
            this::modifyDisposition,
            this::learnGlobalTopic,
            this::exhaustTopic,
            this::reactivateTopic,
            text -> conversationLog.add(new LogEntry.SystemText(text))
        );
    }

    private ConditionContext buildConditionContext() {
        return new ConditionContext(
            playerId, npcId, playerData, disposition,
            playerData.getExhaustedTopicsFor(npcId),
            playerData.getLearnedGlobalTopics()
        );
    }

    private void modifyDisposition(int amount) {
        disposition = DispositionBracket.clampDisposition(disposition + amount);
        playerData.setDispositionFor(npcId, disposition);
    }

    private void learnGlobalTopic(String topicId) {
        playerData.learnGlobalTopic(topicId);
    }

    private void exhaustTopic(String topicId) {
        String effectiveTopicId = (topicId != null && !topicId.isEmpty()) ? topicId : activeTopicId;
        if (effectiveTopicId == null) return;
        playerData.setTopicExhaustion(npcId, effectiveTopicId, ExhaustionState.HIDDEN);
        exhaustTopicFired = true;
    }

    private void reactivateTopic(String topicId) {
        if (topicId == null) return;
        TopicDefinition topic = graph.topics().stream()
            .filter(t -> t.id().equals(topicId))
            .findFirst().orElse(null);
        if (topic != null) {
            String entryNodeId = resolveEntryNodeId(topic);
            DialogueNode node = graph.getNode(entryNodeId);
            if (node instanceof DialogueNode.DialogueTextNode textNode) {
                for (ResponseOption r : textNode.responses()) {
                    grayedExploratories.remove(r.id());
                }
            }
        }
    }

    // --- Topic Resolution ---

    public List<ResolvedTopic> resolveVisibleTopics() {
        ConditionContext condCtx = buildConditionContext();
        Set<String> learned = playerData.getLearnedGlobalTopics();
        Map<String, ExhaustionState> exhausted = playerData.getExhaustedTopicsFor(npcId);

        return graph.topics().stream()
            .map(t -> {
                ExhaustionState state = exhausted.get(t.id());
                if (state == ExhaustionState.HIDDEN) return null;
                if (state == ExhaustionState.GRAYED) return new ResolvedTopic(t, TopicState.GRAYED);
                if (t.scope() == TopicScope.GLOBAL && !t.startLearned() && !learned.contains(t.id())) return null;
                if (!conditionEvaluator.evaluate(t.condition(), condCtx)) return null;
                return new ResolvedTopic(t, TopicState.ACTIVE);
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparingInt(rt -> rt.topic().sortOrder()))
            .toList();
    }

    // --- Session Persistence ---

    private void saveSession() {
        var json = new JsonObject();
        json.addProperty("npcId", npcId);
        json.addProperty("activeNodeId", activeNodeId);
        json.addProperty("activeTopicId", activeTopicId);
        json.addProperty("disposition", disposition);
        json.addProperty("topicsLocked", topicsLocked);

        var followUps = new JsonArray();
        for (String id : pendingFollowUpIds) followUps.add(id);
        json.add("pendingFollowUpIds", followUps);

        var grayed = new JsonArray();
        for (String id : grayedExploratories) grayed.add(id);
        json.add("grayedExploratories", grayed);

        // Serialize conversation log
        var logArray = new JsonArray();
        for (LogEntry entry : conversationLog) {
            var logObj = new JsonObject();
            switch (entry) {
                case LogEntry.TopicHeader h -> {
                    logObj.addProperty("type", "TopicHeader");
                    logObj.addProperty("label", h.label());
                    if (h.questTopic()) logObj.addProperty("questTopic", true);
                }
                case LogEntry.NpcSpeech s -> {
                    logObj.addProperty("type", "NpcSpeech");
                    logObj.addProperty("text", s.text());
                }
                case LogEntry.SelectedResponse s -> {
                    logObj.addProperty("type", "SelectedResponse");
                    logObj.addProperty("responseId", s.responseId());
                    logObj.addProperty("displayText", s.displayText());
                    if (s.statPrefix() != null) logObj.addProperty("statPrefix", s.statPrefix());
                }
                case LogEntry.SystemText s -> {
                    logObj.addProperty("type", "SystemText");
                    logObj.addProperty("text", s.text());
                }
                case LogEntry.ReturnGreeting r -> {
                    logObj.addProperty("type", "ReturnGreeting");
                    logObj.addProperty("text", r.text());
                }
                case LogEntry.ReturnDivider ignored -> {
                    logObj.addProperty("type", "ReturnDivider");
                }
                case LogEntry.SkillCheckResult r -> {
                    logObj.addProperty("type", "SkillCheckResult");
                    logObj.addProperty("statAbbreviation", r.statAbbreviation());
                    logObj.addProperty("skillName", r.skillName());
                    logObj.addProperty("totalRoll", r.totalRoll());
                    logObj.addProperty("passed", r.passed());
                    logObj.addProperty("critical", r.critical());
                }
            }
            logArray.add(logObj);
        }
        json.add("log", logArray);

        String serialized = json.toString();
        playerData.setSavedSession(npcId, serialized);
        LOGGER.atInfo().log("Saved session for NPC %s, player %s", npcId, playerId);
    }

    // --- Getters ---

    public String getNpcId() { return npcId; }
    public UUID getPlayerId() { return playerId; }
    public Player getPlayer() { return player; }
    public Ref<EntityStore> getPlayerRef() { return playerRef; }
    public Ref<EntityStore> getNpcRef() { return npcRef; }
    public int getDisposition() { return disposition; }
    public boolean isTopicsLocked() { return topicsLocked; }
    public List<LogEntry> getConversationLog() { return Collections.unmodifiableList(conversationLog); }
    public List<ActiveFollowUp> getActiveFollowUps() { return Collections.unmodifiableList(activeFollowUps); }
    public List<String> getPendingFollowUpIds() { return Collections.unmodifiableList(pendingFollowUpIds); }
    public Set<String> getGrayedExploratories() { return Collections.unmodifiableSet(grayedExploratories); }
    public String getActiveNodeId() { return activeNodeId; }
    public boolean isEnded() { return ended; }
    public DialogueGraph getGraph() { return graph; }
}
