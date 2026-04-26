package com.chonbosmods.dialogue;

import com.chonbosmods.action.ActionContext;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.dialogue.model.ActiveFollowUp;
import com.chonbosmods.topic.MundaneDispositionConstants;
import com.chonbosmods.dice.RollMode;
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
import java.util.concurrent.locks.ReentrantLock;

public class ConversationSession {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final int MAX_ACTION_CHAIN = 10;

    /** Guards all mutable session state. Held by public entry points so that
     *  the SCHEDULER thread (dice roll continuation, page reopen) and the
     *  main server thread (topic/follow-up selection) never mutate state
     *  concurrently. */
    private final ReentrantLock lock = new ReentrantLock();

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
    private ValenceTracker valenceTracker;
    // --- CONTINUE chain state (mundane topics only) ---
    private List<String> continueChainNodeIds;
    private int continueChainIndex;
    private boolean statCheckAvailable;
    private String statCheckResponseId;
    private String statCheckResumeNodeId;

    /** One-shot flag set by the nat1 quest-accept handler to suppress response
     *  buttons on the fail node. Consumed and cleared by the next DialogueTextNode
     *  render. Empty responses then trigger the existing returnCheck() path,
     *  which returns the player to topic selection. */
    private boolean suppressNextNodeResponses = false;

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
        this.presenter = presenter;
        this.onSessionEnd = onSessionEnd;
        this.onNpcRelease = onNpcRelease;
        int defaultDisp = npcData.getDefaultDisposition() != 0
            ? npcData.getDefaultDisposition()
            : graph.defaultDisposition();
        this.disposition = playerData.getDispositionFor(npcId, defaultDisp);
        this.valenceTracker = new ValenceTracker();
    }

    // --- Lifecycle ---

    public void start() {
        lock.lock();
        try {
            processNode(graph.greetingNodeId());
            presenter.openInitialPage(resolveVisibleTopics(), disposition);
        } finally {
            lock.unlock();
        }
    }

    public void startFromSaved(List<LogEntry> savedLog, String savedActiveNodeId,
                                String savedActiveTopicId,
                                List<String> savedPendingFollowUps,
                                Set<String> savedGrayedExploratories) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public void endDialogue() {
        lock.lock();
        try {
            if (ended) return;
            ended = true;

            // CONTINUE chain: apply abandonment delta if ending mid-chain
            if (continueChainNodeIds != null
                    && continueChainIndex < continueChainNodeIds.size() - 1) {
                modifyDisposition(MundaneDispositionConstants.TOPIC_ABANDONED);
            }

            playerData.setDispositionFor(npcId, disposition);

            if (valenceTracker.hasRecordedLines()) {
                playerData.setClosingValence(npcId, valenceTracker.getCurrentValence().name());
            }

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
        } finally {
            lock.unlock();
        }
    }

    // --- Topic Selection ---

    public void handleTopicSelected(String topicId) {
        lock.lock();
        try {
        if (topicsLocked || ended) return;

        if (topicId.equals(activeTopicId)
                && continueChainNodeIds != null
                && continueChainIndex < continueChainNodeIds.size() - 1) {
            DialogueNode activeNode = graph.getNode(activeNodeId);
            if (activeNode instanceof DialogueNode.DialogueTextNode activeText) {
                ResponseOption continueOpt = activeText.responses().stream()
                    .filter(r -> r.responseType() == ResponseType.CONTINUE)
                    .findFirst().orElse(null);
                if (continueOpt != null) {
                    handleFollowUpSelected(continueOpt.id());
                    return;
                }
            }
        }

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

        // CONTINUE chain: apply abandonment delta if switching away mid-chain
        if (continueChainNodeIds != null && activeTopicId != null
                && !activeTopicId.equals(topicId)) {
            modifyDisposition(MundaneDispositionConstants.TOPIC_ABANDONED);
        }

        // Normal topic selection: clear stale follow-ups from previous topic
        activeFollowUps = new ArrayList<>();
        pendingFollowUpIds.clear();
        topicsLocked = false;
        exhaustTopicFired = false;
        continueChainNodeIds = null;
        continueChainIndex = 0;
        statCheckAvailable = false;
        statCheckResponseId = null;
        statCheckResumeNodeId = null;
        activeTopicId = topicId;
        conversationLog.add(new LogEntry.TopicHeader(topic.label(), topic.questTopic()));
        presenter.refreshLog(conversationLog);
        presenter.refreshFollowUps(activeFollowUps);

        String entryNodeId = resolveEntryNodeId(topic);
        processNode(entryNodeId);

        // Detect CONTINUE flow
        DialogueNode entryNode = graph.getNode(entryNodeId);
        if (entryNode instanceof DialogueNode.DialogueTextNode textNode) {
            boolean hasContinue = textNode.responses().stream()
                .anyMatch(r -> r.responseType() == ResponseType.CONTINUE);
            if (hasContinue) {
                initContinueChain(entryNodeId, textNode);
            }
        }

        presenter.refreshTopics(resolveVisibleTopics());
        presenter.flushUpdates();
        } finally {
            lock.unlock();
        }
    }

    // --- Follow-Up Selection ---

    public void handleFollowUpSelected(String responseId) {
        lock.lock();
        try {
            if (ended) return;

            DialogueNode node = graph.getNode(activeNodeId);
            if (!(node instanceof DialogueNode.DialogueTextNode textNode)) return;

            ResponseOption selected = textNode.responses().stream()
                .filter(r -> r.id().equals(responseId))
                .findFirst().orElse(null);
            if (selected == null) return;

            markFollowUpSelected(responseId, selected);

            // CONTINUE chain: advance index and handle stat check
            if (continueChainNodeIds != null) {
                if (selected.responseType() == ResponseType.CONTINUE) {
                    continueChainIndex++;
                }

                if (selected.skillCheckRef() != null && statCheckAvailable) {
                    // Player clicked stat check: record resume point and mark used
                    statCheckAvailable = false;
                    // Find the CONTINUE target on this same node (next beat in chain)
                    ResponseOption continueOpt = textNode.responses().stream()
                        .filter(r -> r.responseType() == ResponseType.CONTINUE)
                        .findFirst().orElse(null);
                    statCheckResumeNodeId = continueOpt != null ? continueOpt.targetNodeId() : null;
                }
            }

            if (selected.skillCheckRef() != null) {
                processNode(selected.skillCheckRef());
                presenter.flushUpdates();
                return;
            }

            processNode(selected.targetNodeId());
            presenter.refreshTopics(resolveVisibleTopics());
            presenter.flushUpdates();
        } finally {
            lock.unlock();
        }
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
                    displayText = textNode.speakerText();
                }
                conversationLog.add(new LogEntry.NpcSpeech(displayText));
                ValenceType lineValence = textNode.valence() != null ? textNode.valence() : ValenceType.NEUTRAL;
                valenceTracker.recordNpcLine(lineValence);
                if (suppressNextNodeResponses) {
                    pendingFollowUpIds.clear();
                    activeFollowUps = new ArrayList<>();
                    suppressNextNodeResponses = false;
                } else {
                    filterAndDisplayResponses(textNode);
                }

                if (textNode.exhaustsTopic() && activeTopicId != null) {
                    playerData.setTopicExhaustion(npcId, activeTopicId, ExhaustionState.HIDDEN);
                    exhaustTopicFired = true;
                }

                topicsLocked = textNode.locksConversation();

                if (pendingFollowUpIds.isEmpty()) {
                    // CONTINUE chain: apply completion delta at last beat.
                    // Quest-injected topics reuse the CONTINUE mechanism for turn-in /
                    // TALK_TO_NPC beats but must not tick the mundane disposition bonus.
                    if (continueChainNodeIds != null
                            && continueChainIndex >= continueChainNodeIds.size() - 1
                            && !isActiveTopicQuestTopic()) {
                        modifyDisposition(MundaneDispositionConstants.TOPIC_COMPLETED);
                    }
                    // Skip returnCheck for stat check side-beats: handleSkillCheckResult
                    // injects a CONTINUE response and handles the display after processNode.
                    if (statCheckResumeNodeId == null) {
                        returnCheck();
                    }
                }

                presenter.refreshLog(conversationLog);
                presenter.refreshFollowUps(activeFollowUps);
                presenter.refreshDisposition(disposition);
            }

            case DialogueNode.SkillCheckNode checkNode -> {
                int dc = checkNode.tier().dc();
                RollMode mode = checkNode.affectedByDisposition()
                        ? DispositionBracket.rollMode(disposition)
                        : RollMode.NORMAL;
                PlayerStats stats = PlayerStats.from(playerData);
                presenter.showSkillCheck(checkNode, dc, mode, stats);
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
        lock.lock();
        try {
            // Emit skill check result to dialogue log
            com.chonbosmods.stats.Skill skill = checkNode.skill();
            com.chonbosmods.stats.Stat stat = skill.getStat();

            int xpGained = (result.passed() && playerData != null)
                    ? com.chonbosmods.progression.Nat20XpMath.d20SuccessXp(playerData.getLevel())
                    : 0;

            conversationLog.add(new LogEntry.SkillCheckResult(
                stat.name(), skill.displayName(), result.totalRoll(),
                result.passed(), result.critical(), xpGained
            ));

            if (xpGained > 0) {
                // XpService.award calls store.getComponent, which asserts the world thread.
                // handleSkillCheckResult runs on Nat20-PageTransition, so dispatch to world.execute.
                com.chonbosmods.Natural20 plugin = com.chonbosmods.Natural20.getInstance();
                final int awardXp = xpGained;
                plugin.getDefaultWorld().execute(() ->
                        plugin.getXpService().award(player, playerRef, store, awardXp, "d20:" + stat.name()));
            }

            String nextNodeId = result.passed() ? checkNode.passNodeId() : checkNode.failNodeId();

            // Quest-accept crit consequences (nat20 grant + nat1 disposition/blacklist)
            // only fire inside a quest topic; free-roam and turn-in checks are unaffected.
            // Assumes one skill check per quest topic (the accept gate); if mid-quest
            // checks ever land, gate further on checkNode identity or pass-action.
            if (isActiveTopicQuestTopic() && (result.naturalRoll() == 20 || result.naturalRoll() == 1)) {
                String consequenceText = handleQuestAcceptCrit(result.naturalRoll());
                if (consequenceText != null) {
                    conversationLog.add(new LogEntry.CritConsequence(consequenceText, result.passed()));
                }
            }

            processNode(nextNodeId);

            // CONTINUE chain: inject CONTINUE on the pass/fail node to resume the chain
            if (continueChainNodeIds != null && statCheckResumeNodeId != null) {
                DialogueNode passFailNode = graph.getNode(nextNodeId);
                if (passFailNode instanceof DialogueNode.DialogueTextNode pfText) {
                    @SuppressWarnings("unchecked")
                    List<ResponseOption> pfResponses = (List<ResponseOption>) pfText.responses();
                    if (pfResponses.isEmpty()) {
                        pfResponses.add(new ResponseOption(
                            "stat_check_resume", "CONTINUE", null, statCheckResumeNodeId,
                            ResponseMode.DECISIVE, null, null, null, null,
                            ResponseType.CONTINUE
                        ));
                        // Re-display responses since we just added one
                        filterAndDisplayResponses(pfText);
                        presenter.refreshFollowUps(activeFollowUps);
                    }
                }
                statCheckResumeNodeId = null;
            }

            presenter.refreshTopics(resolveVisibleTopics());
            presenter.flushUpdates();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Apply nat20 / nat1 consequences for a quest-accept skill check. On nat20,
     * grant a fresh-rolled pre-quest item (mirrors the quest's phase-0 tier/ilvl)
     * and stage a "[NPC] hands you [Item]." suffix on the next dialogue line. On
     * nat1, set disposition to the Hostile floor, add the quest id to the
     * player's blacklist (which causes GIVE_QUEST on the fail node to early-return
     * via Task 5's gate), and stage a "[NPC] is insulted..." suffix.
     *
     * <p>Returns silently if the NPC has no pre-generated quest, no settlement
     * record, etc.: this code path also runs for free-roam / turn-in skill checks
     * where there is no quest to grant or blacklist.
     *
     * <p>The nat20 grant runs on the world thread via {@link com.hypixel.hytale.server.core.universe.world.World#execute}
     * and we await the result so the consequence text reflects the granted item.
     * Returns the text to render as a {@link LogEntry.CritConsequence} immediately
     * under the {@link LogEntry.SkillCheckResult} (or {@code null} if there is no
     * pre-generated quest, the nat20 grant failed, or anything else short-circuits).
     */
    private String handleQuestAcceptCrit(int naturalRoll) {
        if (npcData == null || playerData == null) return null;
        com.chonbosmods.settlement.SettlementRegistry settlements =
                com.chonbosmods.Natural20.getInstance().getSettlementRegistry();
        if (settlements == null) return null;

        String cellKey = npcData.getSettlementCellKey();
        if (cellKey == null || cellKey.isEmpty()) return null;

        com.chonbosmods.settlement.SettlementRecord settlement = settlements.getByCell(cellKey);
        if (settlement == null) return null;
        com.chonbosmods.settlement.NpcRecord npcRecord = settlement.getNpcByName(npcId);
        if (npcRecord == null) return null;
        com.chonbosmods.quest.QuestInstance quest = npcRecord.getPreGeneratedQuest();
        if (quest == null) return null;

        String npcDisplayName = npcRecord.getGeneratedName() != null
                ? npcRecord.getGeneratedName() : npcId;

        if (naturalRoll == 20) {
            // giveItem touches the entity store, which asserts the world thread.
            // handleSkillCheckResult runs on Nat20-PageTransition, so dispatch the
            // grant via world.execute and await the resulting ItemStack so the
            // consequence text can name the granted item.
            ActionContext ctx = buildActionContext();
            com.chonbosmods.Natural20 plugin = com.chonbosmods.Natural20.getInstance();
            java.util.concurrent.CompletableFuture<com.hypixel.hytale.server.core.inventory.ItemStack> future
                    = new java.util.concurrent.CompletableFuture<>();
            plugin.getDefaultWorld().execute(() -> {
                try {
                    future.complete(DialogueActionRegistry.rollAndGrantPreQuestReward(ctx, quest));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            com.hypixel.hytale.server.core.inventory.ItemStack granted;
            try {
                granted = future.get(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log(
                        "nat20 grant await failed (giveItem may have timed out or world unloaded mid-dialogue); "
                            + "skipping pre-quest item consequence");
                return null;
            }
            if (granted == null) return null;
            return npcDisplayName + " hands you " + resolveItemDisplayName(granted) + ".";
        }

        // naturalRoll == 1
        playerData.setDispositionFor(npcId,
                DispositionBracket.HOSTILE.getMaxDisposition());
        playerData.addBlacklistedQuest(quest.getQuestId());
        playerData.setTopicExhaustion(npcId, activeTopicId,
                com.chonbosmods.dialogue.model.ExhaustionState.HIDDEN);
        // exhaustTopicFired makes returnCheck() take the Step 1 early-return path
        // so the entry node's ACCEPT/DECLINE responses are not redisplayed when the
        // suppressed fail-node render falls through to returnCheck().
        exhaustTopicFired = true;
        suppressNextNodeResponses = true;
        return npcDisplayName + " is insulted and no longer wants your help.";
    }

    private static String resolveItemDisplayName(
            com.hypixel.hytale.server.core.inventory.ItemStack stack) {
        com.chonbosmods.loot.Nat20LootData data =
                stack.getFromMetadataOrNull(com.chonbosmods.loot.Nat20LootData.METADATA_KEY);
        if (data != null && data.getGeneratedName() != null && !data.getGeneratedName().isBlank()) {
            return data.getGeneratedName();
        }
        return stack.getItemId();
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
            .filter(r -> statCheckResponseId == null || !r.id().equals(statCheckResponseId) || statCheckAvailable)
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
                    opt.id(), opt.displayText(), opt.logText(), opt.statPrefix(), grayed,
                    opt.responseType()));
        }

    }

    private void markFollowUpSelected(String selectedId, ResponseOption selected) {
        // Record the selection in the conversation log (history only).
        // Skip for skill checks: their result is logged as SkillCheckResult instead.
        // CONTINUE responses show as a divider rather than "> CONTINUE".
        if (selected.responseType() == ResponseType.CONTINUE) {
            conversationLog.add(new LogEntry.ReturnDivider());
        } else if (selected.skillCheckRef() == null) {
            String logDisplay = selected.logText() != null ? selected.logText()
                : selected.displayText() != null ? selected.displayText()
                : "...";
            conversationLog.add(new LogEntry.SelectedResponse(selectedId, logDisplay, selected.statPrefix()));
        }

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

        // CONTINUE chain: mark topic done when chain is complete
        if (continueChainNodeIds != null) {
            if (continueChainIndex >= continueChainNodeIds.size() - 1) {
                playerData.setTopicExhaustion(npcId, activeTopicId, ExhaustionState.GRAYED);
                playerData.setTopicRecapNode(npcId, activeTopicId, activeNodeId);
                activeFollowUps = List.of();
                pendingFollowUpIds.clear();
                activeTopicId = null;
                topicsLocked = false;
                continueChainNodeIds = null;
            }
            presenter.refreshLog(conversationLog);
            presenter.refreshFollowUps(activeFollowUps);
            presenter.refreshTopics(resolveVisibleTopics());
            presenter.refreshDisposition(disposition);
            return;
        }

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

    private void initContinueChain(String entryNodeId, DialogueNode.DialogueTextNode entryNode) {
        continueChainNodeIds = new ArrayList<>();
        continueChainNodeIds.add(entryNodeId);
        continueChainIndex = 0;

        // Reset stat check state; scanForStatCheck populates it if any chain node has a check.
        statCheckAvailable = false;
        statCheckResponseId = null;
        scanForStatCheck(entryNode);

        // Walk the CONTINUE chain to collect all node IDs, scanning each beat
        // for stat check responses. TopicGraphBuilder places the stat check on
        // a random beat (entry or any later beat), so scanning only the entry
        // would miss checks on mid-chain or last beats and leave
        // statCheckResumeNodeId unset, stranding the player on the pass/fail
        // node with no CONTINUE injected.
        DialogueNode.DialogueTextNode current = entryNode;
        while (true) {
            ResponseOption continueOpt = current.responses().stream()
                .filter(r -> r.responseType() == ResponseType.CONTINUE)
                .findFirst().orElse(null);
            if (continueOpt == null) break;
            String nextId = continueOpt.targetNodeId();
            continueChainNodeIds.add(nextId);
            DialogueNode nextNode = graph.getNode(nextId);
            if (nextNode instanceof DialogueNode.DialogueTextNode nextText) {
                current = nextText;
                scanForStatCheck(current);
            } else {
                break;
            }
        }
    }

    private void scanForStatCheck(DialogueNode.DialogueTextNode node) {
        if (statCheckResponseId != null) return;
        for (ResponseOption r : node.responses()) {
            if (r.skillCheckRef() != null) {
                statCheckAvailable = true;
                statCheckResponseId = r.id();
                return;
            }
        }
    }

    private boolean isActiveTopicQuestTopic() {
        if (activeTopicId == null) return false;
        return graph.topics().stream()
            .filter(t -> t.id().equals(activeTopicId))
            .findFirst()
            .map(TopicDefinition::questTopic)
            .orElse(false);
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

        if (continueChainNodeIds != null) {
            json.addProperty("continueChainIndex", continueChainIndex);
            json.addProperty("statCheckAvailable", statCheckAvailable);
            if (statCheckResponseId != null) json.addProperty("statCheckResponseId", statCheckResponseId);
        }

        // Serialize conversation log
        var logArray = new JsonArray();
        for (LogEntry entry : conversationLog) {
            logArray.add(LogEntry.toJson(entry));
        }
        json.add("log", logArray);

        // Serialize valence tracker state
        ValenceTracker.State valenceState = valenceTracker.getState();
        var valenceArr = new JsonArray();
        for (ValenceType v : valenceState.recentValences()) valenceArr.add(v.name());
        json.add("valenceRecentValences", valenceArr);
        json.addProperty("valenceTrajectoryScore", valenceState.trajectoryScore());

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
    public Nat20PlayerData getPlayerData() { return playerData; }
    public int getDisposition() { return disposition; }
    public boolean isTopicsLocked() { return topicsLocked; }
    public List<LogEntry> getConversationLog() { return Collections.unmodifiableList(conversationLog); }
    public List<ActiveFollowUp> getActiveFollowUps() { return Collections.unmodifiableList(activeFollowUps); }
    public List<String> getPendingFollowUpIds() { return Collections.unmodifiableList(pendingFollowUpIds); }
    public Set<String> getGrayedExploratories() { return Collections.unmodifiableSet(grayedExploratories); }
    public String getActiveNodeId() { return activeNodeId; }
    public boolean isEnded() { return ended; }
    public DialogueGraph getGraph() { return graph; }
    public ValenceTracker getValenceTracker() { return valenceTracker; }
    /** Seed the valence tracker with an opening valence derived from drift evaluation.
     *  Must be called before start() or startFromSaved(). */
    void seedValenceTracker(ValenceType openingValence) {
        this.valenceTracker = new ValenceTracker(openingValence);
    }
}
