package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.background.Background;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds and assigns the tutorial quest ("tutorial_main") to a player immediately
 * after background commit. The quest structure is fixed: three phases that
 * exercise the primary objective types.
 *
 * <p>Phase 1 is pre-marked ready-to-turn-in so the player can walk straight to
 * Celius without any intermediate objective. Phases 2 and 3 are stubs in Piece
 * 1B (targetId "deferred_npc" and "deferred_boss"); Pieces 2 and 3 will fill
 * in real settlement/NPC and POI/boss content respectively.
 */
public final class TutorialQuestFactory {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|TutorialQuestFactory");

    public static final String QUEST_ID = "tutorial_main";
    public static final String SITUATION_ID = "tutorial";
    public static final String SOURCE_NPC_ID = "CeliusGravus";
    public static final String SOURCE_SETTLEMENT_ID = "0,0";
    public static final String QUEST_TOPIC_HEADER = "A Matter of Urgency";

    private TutorialQuestFactory() {}

    /**
     * Build the tutorial quest and assign it to the player's active-quest map.
     * Idempotent: no-op when the quest is already active or has been completed.
     */
    public static void createAndAssign(Nat20PlayerData playerData, Background background, UUID playerUuid) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) {
            LOGGER.atWarning().log("createAndAssign: QuestSystem not initialized; skipping");
            return;
        }
        QuestStateManager stateManager = questSystem.getStateManager();

        if (stateManager.getQuest(playerData, QUEST_ID) != null) {
            LOGGER.atInfo().log("createAndAssign: %s already active for %s, skipping",
                QUEST_ID, playerUuid);
            return;
        }
        if (stateManager.getCompletedQuestIds(playerData).contains(QUEST_ID)) {
            LOGGER.atInfo().log("createAndAssign: %s already completed for %s, skipping",
                QUEST_ID, playerUuid);
            return;
        }

        List<ObjectiveInstance> objectives = new ArrayList<>();

        // Phase 1: pre-completed TALK_TO_NPC so state starts READY_FOR_TURN_IN.
        // Celius bypasses the procedural talk-to-npc injector (see DialogueManager),
        // so this target does not auto-register a dialogue topic on him.
        ObjectiveInstance phase1 = new ObjectiveInstance(
            ObjectiveType.TALK_TO_NPC, SOURCE_NPC_ID, "Speak with Celius Gravus",
            1, null, SOURCE_SETTLEMENT_ID);
        phase1.markComplete();
        objectives.add(phase1);

        // Phase 2 stub: Piece 2 will replace with a real settlement NPC.
        ObjectiveInstance phase2 = new ObjectiveInstance(
            ObjectiveType.TALK_TO_NPC, "deferred_npc", "More coming soon.",
            1, null, null);
        objectives.add(phase2);

        // Phase 3 stub: Piece 3 will replace with a pre-rolled hostile POI boss.
        ObjectiveInstance phase3 = new ObjectiveInstance(
            ObjectiveType.KILL_BOSS, "deferred_boss", "More coming soon.",
            1, null, null);
        objectives.add(phase3);

        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("quest_topic_header", QUEST_TOPIC_HEADER);
        bindings.put("quest_objective_summary", "Return to Celius Gravus");
        if (background != null) {
            bindings.put("Background", background.displayName());
        }
        // npc_x/npc_z anchor the nearest-other-settlement search used by
        // tryResolveDeferredTalkToNpc at phase 2 turn-in. Seed from Celius's
        // settlement if the placement already landed; otherwise fall back to
        // world-origin (spawn is near origin in practice).
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        double anchorX = 0, anchorZ = 0;
        if (settlements != null) {
            SettlementRecord spawn = settlements.getByCell(SOURCE_SETTLEMENT_ID);
            if (spawn != null) {
                anchorX = spawn.getPosX();
                anchorZ = spawn.getPosZ();
            }
        }
        bindings.put("npc_x", Double.toString(anchorX));
        bindings.put("npc_z", Double.toString(anchorZ));

        QuestInstance quest = new QuestInstance(
            QUEST_ID, SITUATION_ID, SOURCE_NPC_ID, SOURCE_SETTLEMENT_ID,
            objectives, bindings);
        quest.setMaxConflicts(2);
        quest.setState(QuestState.READY_FOR_TURN_IN);
        quest.setAccepters(List.of(playerUuid));

        stateManager.addQuest(playerData, quest);

        // Best-effort resolve of the phase 2 target NPC at creation time so the
        // phase-1-turn-in dialogue can interpolate {target_npc}/{target_npc_settlement}
        // on the player's first visit to Celius. If no other settlement exists
        // yet, the objective stays deferred and TUTORIAL_TURN_IN_PHASE_1 retries.
        boolean resolved = DialogueActionRegistry.tryResolveDeferredTalkToNpc(quest, phase2);
        if (resolved) {
            bindings.putIfAbsent("target_npc_opener",
                "You're the one Celius sent. Good. Take back what I'm about to tell you, and tell him I said to move quickly.");
            bindings.putIfAbsent("target_npc_closer",
                "Go on, then. Don't keep Celius waiting.");
        }

        LOGGER.atInfo().log("Created tutorial quest for %s (background=%s, phase2Resolved=%s)",
            playerUuid, background != null ? background.name() : "null", resolved);
    }
}
