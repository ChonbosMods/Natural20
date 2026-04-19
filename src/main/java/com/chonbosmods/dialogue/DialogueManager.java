package com.chonbosmods.dialogue;

import com.chonbosmods.Natural20;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.quest.DialogueResolver;
import com.chonbosmods.quest.ObjectiveInstance;
import com.chonbosmods.quest.ObjectiveType;
import com.chonbosmods.quest.QuestCompletionBanner;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestSystem;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
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
    /** Quests waiting to fire their completion banner once the player's dialogue session ends.
     *  Used by TALK_TO_NPC objectives so the banner doesn't render on top of the dialogue UI. */
    private final Map<UUID, List<QuestInstance>> pendingBanners = new ConcurrentHashMap<>();
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
        injectQuestAvailableTopics(graph, npcId, npcData, playerData);
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
                player, player.getPlayerRef(), playerRef, store, this, displayName);

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
            // Fire any banners deferred during the session (e.g. TALK_TO_NPC completions)
            firePendingBanners(playerUuid, session);
            LOGGER.atFine().log("Ended dialogue session for player %s", playerUuid);
        }
    }

    /**
     * Queue a quest's completion banner to fire once the player's current dialogue
     * session ends. Used by TALK_TO_NPC completion so the banner doesn't render on
     * top of the dialogue UI. The banner respects the per-phase first-fire rule
     * via {@link QuestInstance#markPhaseReadyForTurnIn()}.
     */
    public void queueBannerOnSessionEnd(UUID playerUuid, QuestInstance quest) {
        pendingBanners.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(quest);
    }

    private void firePendingBanners(UUID playerUuid, ConversationSession session) {
        List<QuestInstance> queued = pendingBanners.remove(playerUuid);
        if (queued == null || queued.isEmpty()) return;
        Player player = session.getPlayer();
        if (player == null) return;
        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> playerRef
                = session.getPlayerRef();
        var store = playerRef.getStore();
        com.chonbosmods.data.Nat20PlayerData playerData = session.getPlayerData();
        for (QuestInstance quest : queued) {
            if (quest.markPhaseReadyForTurnIn()) {
                QuestCompletionBanner.show(player.getPlayerRef(), quest);
                if (playerData != null) {
                    int xp = com.chonbosmods.progression.Nat20XpMath.questPhaseXp(playerData.getLevel());
                    com.chonbosmods.Natural20.getInstance().getXpService()
                            .award(player, playerRef, store, xp, "quest:" + quest.getQuestId());
                }
            }
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
            // Prefer the NPC's pre-generated quest binding so dialogue text agrees
            // with the POI spawn descriptor (quest.variableBindings is the single
            // source of truth for mob_type on a per-quest basis).
            var npcRecord = settlement.getNpcByName(npcData.getGeneratedName());
            QuestInstance quest = npcRecord != null ? npcRecord.getPreGeneratedQuest() : null;
            String chosen;
            if (quest != null && quest.getVariableBindings().containsKey("mob_type")) {
                chosen = quest.getVariableBindings().get("mob_type");
            } else {
                var random = new Random(cellKey.hashCode() ^ 13);
                chosen = mobTypes.get(random.nextInt(mobTypes.size()));
                if (quest != null) quest.getVariableBindings().put("mob_type", chosen);
            }
            bindings.put("mob_type", chosen);
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
    private void injectQuestAvailableTopics(DialogueGraph graph, String npcId,
                                            Nat20NpcData npcData,
                                            Nat20PlayerData playerData) {
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
        int playerLevel = playerData != null ? playerData.getLevel() : 1;

        // The exposition objective is index 0; its values back gather_count, kill_count,
        // quest_item, enemy_type, target_npc when those tokens appear in offer text.
        ObjectiveInstance expositionObj = !quest.getObjectives().isEmpty()
            ? quest.getObjectives().getFirst() : null;

        // Late-bind a deferred TALK_TO_NPC target before resolving exposition text.
        // At quest generation, the registry may have had no other settlements yet
        // (e.g., player teleported to a fresh quest giver). By the time the player
        // opens dialogue, more settlements may exist, so try again now. The
        // resolution mutates `quest.getVariableBindings()` and the objective in
        // place, so subsequent text resolution and the eventual GIVE_QUEST handler
        // both pick up the resolved trio. Persist so the choice survives restart.
        if (expositionObj != null
                && expositionObj.getType() == ObjectiveType.TALK_TO_NPC
                && "deferred_npc".equals(expositionObj.getTargetId())) {
            if (DialogueActionRegistry.tryResolveDeferredTalkToNpc(quest, expositionObj)) {
                settlements.saveAsync();
            }
        }

        String topicHeader = DialogueResolver.resolve(
            b.getOrDefault("quest_topic_header", quest.getSituationId()), b);
        String expositionTemplate = b.getOrDefault("quest_exposition_text", "I need your help with something.");
        String expositionText = DialogueResolver.resolveQuestText(expositionTemplate, b, expositionObj, playerLevel);

        // Diagnostic: trace highlight marker presence in exposition text
        boolean expoHasMarkers = expositionText != null && expositionText.indexOf(EntityHighlight.MARK_START) >= 0;
        LOGGER.atInfo().log(
            "EXPO_DIAG quest=%s template_has_vars=%s resolved_has_markers=%s " +
            "enemy_type_plural=%s expositionObj=%s",
            questId, expositionTemplate.contains("{"),
            expoHasMarkers,
            b.containsKey("enemy_type_plural") ? b.get("enemy_type_plural") : "MISSING",
            expositionObj != null ? expositionObj.getType() + ":" + expositionObj.getTargetLabel() : "null");

        String acceptText = DialogueResolver.resolveQuestText(
            b.getOrDefault("quest_accept_text", "Thank you. Here's what I need."), b, expositionObj, playerLevel);
        String declineText = DialogueResolver.resolveQuestText(
            b.getOrDefault("quest_decline_text", "I understand. Perhaps another time."), b, null, playerLevel);

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

        // Entry node: exposition text with options. If the template defines a
        // skill check, a check option is prepended above ACCEPT that routes through
        // a SkillCheckNode -> pass/fail node, where the player can still ACCEPT/DECLINE
        // after the reveal. The pass branch additionally fires MARK_SKILLCHECK_PASSED
        // (via onEnter) so TURN_IN_V2's reward multiplier picks up the bonus.
        ResponseOption acceptOption = new ResponseOption(
            topicId + "_accept_opt", "ACCEPT", null, acceptNodeId,
            ResponseMode.DECISIVE, null, null, null, null
        );
        ResponseOption declineOption = new ResponseOption(
            topicId + "_decline_opt", "DECLINE", null, declineNodeId,
            ResponseMode.DECISIVE, null, null, null, null
        );

        ResponseOption checkOption = null;
        String skillName = b.get("quest_skillcheck_skill");
        String dcRaw = b.get("quest_skillcheck_dc");
        if (skillName != null && dcRaw != null) {
            try {
                com.chonbosmods.stats.Skill skill = com.chonbosmods.stats.Skill.valueOf(skillName);
                int dc = Integer.parseInt(dcRaw);
                String passText = DialogueResolver.resolveQuestText(
                    b.getOrDefault("quest_skillcheck_pass_text", ""), b, expositionObj, playerLevel);
                String failText = DialogueResolver.resolveQuestText(
                    b.getOrDefault("quest_skillcheck_fail_text", ""), b, expositionObj, playerLevel);

                String checkNodeId = topicId + "_check";
                String passNodeId  = topicId + "_check_pass";
                String failNodeId  = topicId + "_check_fail";

                // Pass node: reveals deeper layer, then offers ACCEPT/DECLINE again so
                // the player chooses with the new information. onEnter stamps the
                // skillcheck-passed flag on the NPC's pre-generated quest.
                List<ResponseOption> passResponses = new ArrayList<>();
                passResponses.add(new ResponseOption(
                    topicId + "_pass_accept_opt", "ACCEPT", null, acceptNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
                passResponses.add(new ResponseOption(
                    topicId + "_pass_decline_opt", "DECLINE", null, declineNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
                graph.nodes().put(passNodeId, new DialogueNode.DialogueTextNode(
                    passText, null, passResponses,
                    List.of(Map.of("type", DialogueActionRegistry.MARK_SKILLCHECK_PASSED)),
                    false, false, ValenceType.POSITIVE
                ));

                // Fail node: NPC deflects; player still gets ACCEPT/DECLINE.
                List<ResponseOption> failResponses = new ArrayList<>();
                failResponses.add(new ResponseOption(
                    topicId + "_fail_accept_opt", "ACCEPT", null, acceptNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
                failResponses.add(new ResponseOption(
                    topicId + "_fail_decline_opt", "DECLINE", null, declineNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
                graph.nodes().put(failNodeId, new DialogueNode.DialogueTextNode(
                    failText, null, failResponses, List.of(), false, false, ValenceType.NEUTRAL
                ));

                // Skill check node: routes pass/fail.
                graph.nodes().put(checkNodeId, new DialogueNode.SkillCheckNode(
                    skill, null, dc, false, passNodeId, failNodeId, List.of()
                ));

                // Skill check option: triggers the check. Uses skillCheckRef instead
                // of targetNodeId; ConversationSession routes selections with
                // skillCheckRef directly to the SkillCheckNode. displayText is the
                // bare skill name and statPrefix carries the stat code so
                // Nat20DialoguePage paints the [STAT] bracket in the stat color,
                // matching how authored skill-check responses render.
                checkOption = new ResponseOption(
                    topicId + "_check_opt",
                    skill.displayName(),
                    null, null,
                    ResponseMode.DECISIVE, null,
                    checkNodeId, skill.getStat().name(), null
                );
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("Quest %s skill check has invalid skill='%s' or dc='%s', skipping check option",
                    questId, skillName, dcRaw);
            }
        }

        // Order: skill check (if any), ACCEPT, DECLINE.
        List<ResponseOption> responses = new ArrayList<>();
        if (checkOption != null) responses.add(checkOption);
        responses.add(acceptOption);
        responses.add(declineOption);

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
     * Inject turn-in topic for quests in READY_FOR_TURN_IN or AWAITING_CONTINUATION state.
     *
     * <p>Flow: clicking the topic enters an entry node whose {@code onEnter} fires
     * TURN_IN_V2 (consumes items, claims reward, parks in AWAITING_CONTINUATION). The
     * entry node's speech is the turn-in narration the player reads as a result of
     * the action. A single [CONTINUE] response then routes through a CONTINUE_QUEST
     * action node which advances {@code conflictCount}, sets up the next objective,
     * and lands on the conflict-text or resolution node.
     *
     * <p>The two-phase split exists so that closing the dialog after seeing the
     * turn-in text but before clicking [CONTINUE] is safe: the consume/reward work
     * is already done (and TURN_IN_V2 no-ops on re-fire), but the next objective
     * isn't placed until the player explicitly continues. Re-opening the dialog
     * re-injects the topic with the same turn-in text and the same [CONTINUE] route.
     */
    private void injectQuestTurnInTopics(DialogueGraph graph, String npcId, Nat20PlayerData playerData) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);
        for (QuestInstance quest : quests.values()) {
            if (!npcId.equals(quest.getSourceNpcId())) continue;
            com.chonbosmods.quest.QuestState qs = quest.getState();
            if (qs != com.chonbosmods.quest.QuestState.READY_FOR_TURN_IN
                    && qs != com.chonbosmods.quest.QuestState.AWAITING_CONTINUATION) continue;

            String questId = quest.getQuestId();
            Map<String, String> b = quest.getVariableBindings();
            int cc = quest.getConflictCount();

            // The current objective backs the turn-in text; the NEXT objective (if a
            // conflict is queued) backs the inline conflict text shown after [Turn in].
            List<ObjectiveInstance> objs = quest.getObjectives();
            ObjectiveInstance currentObj = cc < objs.size() ? objs.get(cc) : null;
            ObjectiveInstance nextObj = (cc + 1) < objs.size() ? objs.get(cc + 1) : null;

            // Late-bind deferred TALK_TO_NPC on nextObj before resolving conflict
            // text that references {target_npc}/{target_npc_settlement}. Without
            // this the overlay writes "someone nearby" and the settlement stays
            // literal, even though CONTINUE_QUEST resolves the objective later.
            if (nextObj != null
                    && nextObj.getType() == ObjectiveType.TALK_TO_NPC
                    && "deferred_npc".equals(nextObj.getTargetId())) {
                SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
                if (DialogueActionRegistry.tryResolveDeferredTalkToNpc(quest, nextObj)
                        && settlements != null) {
                    settlements.saveAsync();
                }
            }

            // Select turn-in text based on conflict count
            String turnInTextKey = switch (cc) {
                case 0 -> "quest_exposition_turnin_text";
                case 1 -> "quest_conflict1_turnin_text";
                case 2 -> "quest_conflict2_turnin_text";
                case 3 -> "quest_conflict3_turnin_text";
                case 4 -> "quest_conflict4_turnin_text";
                default -> "quest_exposition_turnin_text";
            };
            String turnInTemplate = b.getOrDefault(turnInTextKey, "You're back. Tell me what happened.");
            String turnInText = DialogueResolver.resolveQuestText(turnInTemplate, b, currentObj);

            // Diagnostic: trace highlight marker presence in resolved turn-in text
            boolean hasMarkers = turnInText != null && turnInText.indexOf(EntityHighlight.MARK_START) >= 0;
            LOGGER.atInfo().log(
                "TURNIN_DIAG quest=%s cc=%d template_key=%s has_target_npc=%s has_enemy_type=%s " +
                "currentObj=%s template_has_vars=%s resolved_has_markers=%s",
                questId, cc, turnInTextKey,
                b.containsKey("target_npc") ? EntityHighlight.stripMarkers(b.get("target_npc")) : "MISSING",
                b.containsKey("enemy_type") ? EntityHighlight.stripMarkers(b.get("enemy_type")) : "MISSING",
                currentObj != null ? currentObj.getType() + ":" + currentObj.getTargetLabel() : "null",
                turnInTemplate.contains("{"),
                hasMarkers);

            // Conflict text (for the NEXT conflict, if triggered)
            String conflictTextKey = switch (cc) {
                case 0 -> "quest_conflict1_text";
                case 1 -> "quest_conflict2_text";
                case 2 -> "quest_conflict3_text";
                case 3 -> "quest_conflict4_text";
                default -> "";
            };
            String conflictTemplate = b.getOrDefault(conflictTextKey, "");
            String conflictText = DialogueResolver.resolveQuestText(conflictTemplate, b, nextObj);

            // Diagnostic: trace conflict text too
            boolean conflictHasMarkers = conflictText != null && conflictText.indexOf(EntityHighlight.MARK_START) >= 0;
            if (!conflictTemplate.isEmpty()) {
                LOGGER.atInfo().log(
                    "CONFLICT_DIAG quest=%s cc=%d template_key=%s nextObj=%s " +
                    "template_has_vars=%s resolved_has_markers=%s",
                    questId, cc, conflictTextKey,
                    nextObj != null ? nextObj.getType() + ":" + nextObj.getTargetLabel() : "null",
                    conflictTemplate.contains("{"),
                    conflictHasMarkers);
            }

            String resolutionText = DialogueResolver.resolveQuestText(
                b.getOrDefault("quest_resolution_text", "Thank you. You've done well."), b, currentObj);

            String topicHeader = DialogueResolver.resolve(
                b.getOrDefault("quest_topic_header", quest.getSituationId()), b);

            String topicId = "questturnin_" + questId + "_c" + cc;
            String entryNodeId = topicId + "_entry";
            String continueNodeId = topicId + "_continue";
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

            // [CONTINUE] target is deterministic (decided at quest generation): next
            // conflict node if more conflicts are queued, otherwise the resolution.
            String afterContinue = quest.hasMoreConflicts() ? conflictNodeId : resolutionNodeId;

            // Continue action node: fires CONTINUE_QUEST (advances conflictCount,
            // sets up next objective POI/markers) and lands on the conflict or
            // resolution text node.
            graph.nodes().put(continueNodeId, new DialogueNode.ActionNode(
                List.of(Map.of("type", DialogueActionRegistry.CONTINUE_QUEST, "questId", questId)),
                afterContinue, List.of(), false
            ));

            // Entry node: TURN_IN_V2 fires onEnter (items consumed, reward stamped,
            // state parked in AWAITING_CONTINUATION) so the turn-in text reads as
            // the narration of the action that just occurred. [CONTINUE] then routes
            // through the action node above.
            graph.nodes().put(entryNodeId, new DialogueNode.DialogueTextNode(
                turnInText, null,
                List.of(new ResponseOption(
                    topicId + "_continue_resp", "[CONTINUE]", null, continueNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                )),
                List.of(Map.of("type", DialogueActionRegistry.TURN_IN_V2, "questId", questId)),
                false, false, ValenceType.NEUTRAL
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
                // For templates with multiple TALK_TO_NPC objectives, pick the
                // opener/closer pair matching this occurrence (1st vs 2nd visit).
                String questId = quest.getQuestId();
                Map<String, String> b = quest.getVariableBindings();
                int talkOccurrence = countTalkToNpcBefore(quest);
                String openerKey = talkOccurrence >= 2 ? "target_npc_opener_2" : "target_npc_opener";
                String closerKey = talkOccurrence >= 2 ? "target_npc_closer_2" : "target_npc_closer";
                String openerText = DialogueResolver.resolveQuestText(
                    b.getOrDefault(openerKey,
                        b.getOrDefault("target_npc_opener",
                            "You're looking into the situation? I can tell you what I know.")),
                    b, obj);
                String closerText = DialogueResolver.resolveQuestText(
                    b.getOrDefault(closerKey,
                        b.getOrDefault("target_npc_closer",
                            "That's all I can tell you. Pass it along.")),
                    b, obj);
                String topicLabel = b.getOrDefault("quest_topic_header", quest.getSituationId());
                String questGiver = quest.getSourceNpcId();

                String topicId = "talknpc_" + questId;
                String entryNodeId = topicId + "_entry";
                String closerNodeId = topicId + "_closer";
                String actionNodeId = topicId + "_action";

                // Action: COMPLETE_TALK_TO_NPC, then show closer text
                graph.nodes().put(actionNodeId, new DialogueNode.ActionNode(
                    List.of(Map.of("type", "COMPLETE_TALK_TO_NPC", "questId", questId)),
                    closerNodeId, List.of(), true
                ));

                // Closer: target NPC wraps up (no response options, terminal node)
                graph.nodes().put(closerNodeId, new DialogueNode.DialogueTextNode(
                    closerText, null, List.of(), List.of(), true, false, ValenceType.NEUTRAL
                ));

                // Entry: target NPC opens the conversation, [CONTINUE] completes the objective
                graph.nodes().put(entryNodeId, new DialogueNode.DialogueTextNode(
                    openerText, null,
                    List.of(new ResponseOption(
                        topicId + "_continue", "[CONTINUE]", null, actionNodeId,
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

    /**
     * Count how many TALK_TO_NPC objectives exist at or before the current objective
     * index. Returns 1 for the first occurrence, 2 for the second, etc.
     */
    private int countTalkToNpcBefore(QuestInstance quest) {
        int currentIdx = quest.getConflictCount();
        int count = 0;
        List<ObjectiveInstance> objectives = quest.getObjectives();
        for (int i = 0; i <= currentIdx && i < objectives.size(); i++) {
            if (objectives.get(i).getType() == ObjectiveType.TALK_TO_NPC) {
                count++;
            }
        }
        return count;
    }
}
