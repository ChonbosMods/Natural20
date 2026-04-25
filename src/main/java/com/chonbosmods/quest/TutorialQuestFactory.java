package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.action.DialogueActionRegistry;
import com.chonbosmods.background.Background;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.model.DifficultyConfig;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
    /** Matches the generated name set by {@code SettlementWorldGenListener.renameFirstGuardToCelius}.
     *  Used as {@code QuestInstance.sourceNpcId}, so {@code SettlementRecord.getNpcByName}
     *  lookups in {@code COMPLETE_TALK_TO_NPC} / {@code POIKillTrackingSystem} find him and
     *  stamp the turn-in marker. The dialogue-graph key is still {@code "CeliusGravus"}
     *  (no space) because that's the JSON file's {@code npcId}; the two namespaces are
     *  intentionally separate. */
    public static final String SOURCE_NPC_ID = "Celius Gravus";
    public static final String QUEST_TOPIC_HEADER = "A Matter of Urgency";
    /** Difficulty config id used for the tutorial quest. Drives phase-3 mob/boss ilvl,
     *  populationSpec, XP, and reward tier range. "easy" ⇒ Common-Uncommon rewards. */
    public static final String DIFFICULTY_ID = "easy";

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

        // Ensure the runtime playerUuid is bound to the player data BEFORE any
        // quest-state read. getActiveQuests routes through
        // data.getPlayerUuid() to filter the party-quest store; if that field
        // is null we silently get an empty quest list and refreshMarkers
        // caches zero markers. Mirrors the pattern in DialogueManager.startSession
        // and BackgroundCommitter.commit in case the earlier bind didn't run.
        if (playerData.getPlayerUuid() == null) {
            playerData.setPlayerUuid(playerUuid);
        }

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

        // Find the settlement that hosts Celius (flagged in SettlementWorldGenListener).
        // The cell key is whatever cell the world spawn fell into this world, not
        // a hardcoded "0,0". If Celius's settlement hasn't placed yet we still create
        // the quest but with a null sourceSettlementId; the return waypoint will no-op
        // until the cell loads and the player can still find him in the world.
        SettlementRegistry settlements = Natural20.getInstance().getSettlementRegistry();
        SettlementRecord celiusSettlement = findCeliusSettlement(settlements);
        String sourceSettlementId = celiusSettlement != null ? celiusSettlement.getCellKey() : null;

        List<ObjectiveInstance> objectives = new ArrayList<>();

        // Phase 1: pre-completed TALK_TO_NPC so state starts READY_FOR_TURN_IN.
        // Celius bypasses the procedural talk-to-npc injector (see DialogueManager),
        // so this target does not auto-register a dialogue topic on him.
        ObjectiveInstance phase1 = new ObjectiveInstance(
            ObjectiveType.TALK_TO_NPC, SOURCE_NPC_ID, "Speak with Celius Gravus",
            1, null, sourceSettlementId);
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
        bindings.put("quest_objective_summary", "Speak with Celius Gravus");
        if (background != null) {
            bindings.put("Background", background.displayName());
        }
        // npc_x/npc_z anchor the nearest-other-settlement search used by
        // tryResolveDeferredTalkToNpc at phase 2 turn-in. Seed from Celius's
        // settlement if it has placed; otherwise fall back to 0,0 (resolver
        // will still find something).
        double anchorX = celiusSettlement != null ? celiusSettlement.getPosX() : 0;
        double anchorZ = celiusSettlement != null ? celiusSettlement.getPosZ() : 0;
        bindings.put("npc_x", Double.toString(anchorX));
        bindings.put("npc_z", Double.toString(anchorZ));

        QuestInstance quest = new QuestInstance(
            QUEST_ID, SITUATION_ID, SOURCE_NPC_ID, sourceSettlementId,
            objectives, bindings);
        quest.setMaxConflicts(2);
        quest.setDifficultyId(DIFFICULTY_ID);
        quest.setState(QuestState.READY_FOR_TURN_IN);
        quest.setAccepters(List.of(playerUuid));

        // Set up per-phase rewards. Phase 1 stays null per design (narrative-only
        // acknowledgement). Phase 2 and 3 store rewardTier + areaLevel + ilvlBonus
        // and defer the actual AffixRewardRoller call to dispensePhaseReward at
        // turn-in (Q6: per-player roll using the recipient's playerLevel).
        rollTutorialRewards(quest);

        // Pre-roll the phase-3 boss now so {boss_name} is bound before the
        // player opens any Celius dialogue session. DialogueGraph.lateResolve
        // runs once at session start and the phase-2 assign node's speakerText
        // references {boss_name}; without this it would render as "".
        TutorialPhase3Setup.preRollBoss(quest, phase3);

        // Commit the quest to the party-quest store. Without this the quest
        // exists only as a local object; getActiveQuests returns empty and
        // refreshMarkers caches zero markers. (Regression from e4c173a8 where
        // adding rollTutorialRewards accidentally replaced the addQuest line.)
        stateManager.addQuest(playerData, quest);

        // Stamp the phase-1 turn-in marker on Celius now. The quest is born in
        // READY_FOR_TURN_IN state (phase 1 is pre-complete), so the "?" should
        // show from the moment the player walks toward him.
        if (celiusSettlement != null) {
            NpcRecord celius = celiusSettlement.getNpcByName(SOURCE_NPC_ID);
            if (celius != null) {
                celius.setMarkerState("QUEST_TURN_IN");
                if (celius.getEntityUUID() != null) {
                    com.chonbosmods.marker.QuestMarkerManager.INSTANCE.syncMarker(
                        celius.getEntityUUID(),
                        com.chonbosmods.data.Nat20NpcData.QuestMarkerState.QUEST_TURN_IN);
                }
                settlements.saveAsync();
            }
        }

        // Refresh the player's map waypoints so Celius's settlement lights up
        // with the blue RETURN marker as soon as Jiub hands them off. Without
        // this, the marker only appears after the next refresh trigger (login,
        // quest-log toggle, etc.) and the first tutorial leg feels silent.
        com.chonbosmods.waypoint.QuestMarkerProvider.refreshMarkers(playerUuid, playerData);

        // Best-effort resolve of the phase 2 target NPC at creation time so the
        // phase-1-turn-in dialogue can interpolate {target_npc}/{target_npc_settlement}
        // on the player's first visit to Celius. If no other settlement exists
        // yet, the objective stays deferred and TUTORIAL_TURN_IN_PHASE_1 retries.
        boolean resolved = DialogueActionRegistry.tryResolveDeferredTalkToNpc(quest, phase2);
        if (resolved) {
            bindings.putIfAbsent("target_npc_opener",
                "You're the one Celius sent. Good. I've been tracking the one terrorizing his village for weeks, and I finally have an idea where their lair lies.");
            bindings.putIfAbsent("target_npc_closer",
                "Take word back to Celius, and tell him I said to move on it quickly.");
        }

        LOGGER.atInfo().log("Created tutorial quest for %s (background=%s, celiusCell=%s, phase2Resolved=%s)",
            playerUuid, background != null ? background.name() : "null",
            sourceSettlementId, resolved);
    }

    /**
     * Set up per-phase rewards for the tutorial so {@code dispensePhaseReward}
     * can roll an item per recipient at each turn-in. Phase 1 gets no reward
     * (narrative-only, per design 3). Phase 2 stores the "easy" difficulty's
     * rewardTierMin (Common). Phase 3 50/50s between rewardTierMin and rewardTierMax
     * (Common..Uncommon), matching the original "full range, up to Uncommon" feel.
     *
     * <p>Tutorial uses a hardcoded {@code areaLevel = 1} (starter zone) since the
     * tutorial happens at the player's spawn point. Per Q6 the actual AffixRewardRoller
     * call is deferred to {@code dispensePhaseReward} so each accepter rolls fresh
     * with their own playerLevel clamped against (areaLevel - 5, areaLevel) + ilvlBonus.
     */
    private static void rollTutorialRewards(QuestInstance quest) {
        DifficultyConfig difficulty = Natural20.getInstance().getQuestSystem()
            .getDifficultyRegistry().get(DIFFICULTY_ID);
        if (difficulty == null) {
            LOGGER.atWarning().log("rollTutorialRewards: DifficultyConfig '%s' not found; no rewards",
                DIFFICULTY_ID);
            return;
        }
        quest.setRewardXp(difficulty.xpAmount());

        int areaLevel = 1; // tutorial spawns in the starter zone
        int ilvlBonus = difficulty.ilvlBonus();

        List<QuestInstance.PhaseReward> rewards = new ArrayList<>(3);
        rewards.add(null); // phase 1: narrative-only, no item

        // Phase 2: Common-tier starter-feel item.
        rewards.add(new QuestInstance.PhaseReward(
            difficulty.rewardTierMin(), areaLevel, ilvlBonus));

        // Phase 3: 50/50 between tierMin and tierMax (preserves the old
        // "full range, up to Uncommon" final-phase feel).
        String phase3Tier = ThreadLocalRandom.current().nextBoolean()
            ? difficulty.rewardTierMax()
            : difficulty.rewardTierMin();
        rewards.add(new QuestInstance.PhaseReward(phase3Tier, areaLevel, ilvlBonus));

        quest.setPhaseRewards(rewards);
    }

    /**
     * Scan the settlement registry for the NPC flagged {@code celius_gravus} and
     * return the settlement containing him. Returns null if no Celius-flagged NPC
     * exists yet (e.g. the spawn cell's chunk hasn't loaded); callers should
     * handle this gracefully.
     */
    public static SettlementRecord findCeliusSettlement(SettlementRegistry settlements) {
        if (settlements == null) return null;
        for (SettlementRecord s : settlements.getAll().values()) {
            for (NpcRecord n : s.getNpcs()) {
                if (n.isCeliusGravus()) return s;
            }
        }
        return null;
    }
}
