package com.chonbosmods.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QuestInstance {
    private String questId;
    private String situationId;
    private String sourceNpcId;
    private String sourceSettlementId;
    private String difficultyId;
    private QuestState state = QuestState.AVAILABLE;
    private int conflictCount;
    private int maxConflicts;
    private List<ObjectiveInstance> objectives = new ArrayList<>();
    private Map<String, String> variableBindings = new HashMap<>();
    private Set<Integer> rewardsClaimed = new HashSet<>();
    private boolean skillcheckPassed;

    /** Frozen snapshot of the claimant party's members at accept time. Immutable
     *  for the life of the quest; never mutates on leave/kick/disband/rejoin per
     *  the party & multiplayer quest design (2026-04-21). A solo player is a
     *  single-element list. */
    private List<UUID> accepters = new ArrayList<>();

    // Derived eligibility: accepters evicted by party-proximity rule.
    // Historical `accepters` remains frozen per 2026-04-21 §2.
    private Set<UUID> droppedAccepters = new HashSet<>();

    /** XP awarded on EVERY phase turn-in. Sourced from {@link com.chonbosmods.quest.model.DifficultyConfig#xpAmount()}.
     *  By design each phase of a multi-phase quest grants full XP ("each phase is its own quest"),
     *  so this is the per-phase amount, not a quest-level total. */
    private int rewardXp;

    /** Per-phase rolled rewards, indexed by conflictCount. A 1-phase quest holds 1 entry;
     *  a 3-phase quest holds 3 entries. Rolled at generation by
     *  {@link com.chonbosmods.quest.QuestGenerator} and dispensed one at a time at each
     *  phase turn-in. Non-final phases roll at {@code rewardTierMin} only
     *  (tier-floor dampener), with a 5% bypass to the full {@code rewardTierMin..rewardTierMax}
     *  range; the final phase always rolls the full range unless dampened by objective type
     *  (TALK_TO_NPC / COLLECT_RESOURCES, same 5% bypass). */
    private List<PhaseReward> phaseRewards = new ArrayList<>();

    /** True once the completion banner has fired for the current phase.
     *  Reset to false on phase advance (incrementConflictCount).
     *  Prevents re-firing when revertable objectives (FETCH_ITEM, COLLECT_RESOURCES)
     *  oscillate between ACTIVE_OBJECTIVE and READY_FOR_TURN_IN. */
    private boolean bannerShownForCurrentPhase;

    /** Per-quest waypoint toggle. When true (default), the quest contributes a map
     *  marker via {@code QuestMarkerProvider} and renders normally in the quest log.
     *  When false, the marker is suppressed and the quest-log row is dimmed.
     *  Defaults to true so legacy saves without this field deserialize as enabled. */
    private boolean waypointEnabled = true;

    public QuestInstance() {}

    public QuestInstance(String questId, String situationId, String sourceNpcId,
                         String sourceSettlementId, List<ObjectiveInstance> objectives,
                         Map<String, String> variableBindings) {
        this.questId = questId;
        this.situationId = situationId;
        this.sourceNpcId = sourceNpcId;
        this.sourceSettlementId = sourceSettlementId;
        this.objectives = objectives;
        this.state = QuestState.AVAILABLE;
        this.conflictCount = 0;
        this.variableBindings = variableBindings;
    }

    public String getQuestId() { return questId; }
    public void setQuestId(String questId) { this.questId = questId; }
    public String getSituationId() { return situationId; }
    public String getSourceNpcId() { return sourceNpcId; }
    public String getSourceSettlementId() { return sourceSettlementId; }
    public String getDifficultyId() { return difficultyId; }
    public void setDifficultyId(String difficultyId) { this.difficultyId = difficultyId; }
    public QuestState getState() { return state; }
    public void setState(QuestState state) { this.state = state; }

    /**
     * Sets state to {@link QuestState#READY_FOR_TURN_IN} and reports whether
     * this is the first time the current phase has entered that state.
     *
     * @return true if the caller should fire the completion banner (first time
     *         this phase reached READY_FOR_TURN_IN); false on subsequent
     *         re-entries within the same phase
     */
    public boolean markPhaseReadyForTurnIn() {
        this.state = QuestState.READY_FOR_TURN_IN;
        if (bannerShownForCurrentPhase) return false;
        bannerShownForCurrentPhase = true;
        return true;
    }

    public int getConflictCount() { return conflictCount; }
    public void incrementConflictCount() {
        this.conflictCount++;
        this.bannerShownForCurrentPhase = false;
    }
    public int getMaxConflicts() { return maxConflicts; }
    public void setMaxConflicts(int maxConflicts) { this.maxConflicts = maxConflicts; }
    public boolean hasMoreConflicts() { return conflictCount < maxConflicts; }
    public List<ObjectiveInstance> getObjectives() { return objectives; }
    public Map<String, String> getVariableBindings() { return variableBindings; }
    public Set<Integer> getRewardsClaimed() { return rewardsClaimed; }
    public boolean isSkillcheckPassed() { return skillcheckPassed; }
    public void setSkillcheckPassed(boolean passed) { this.skillcheckPassed = passed; }

    public boolean isWaypointEnabled() { return waypointEnabled; }
    public void setWaypointEnabled(boolean v) { this.waypointEnabled = v; }

    public List<UUID> getAccepters() {
        if (accepters == null) accepters = new ArrayList<>();
        return accepters;
    }

    public void setAccepters(List<UUID> accepters) {
        this.accepters = accepters == null ? new ArrayList<>() : new ArrayList<>(accepters);
    }

    public boolean hasAccepter(UUID player) {
        return getAccepters().contains(player);
    }

    public Set<UUID> droppedAccepters() {
        if (droppedAccepters == null) droppedAccepters = new HashSet<>();
        return droppedAccepters;
    }

    public void dropAccepter(UUID player) {
        if (droppedAccepters == null) droppedAccepters = new HashSet<>();
        droppedAccepters.add(player);
    }

    public boolean isEligible(UUID player) {
        if (!accepters.contains(player)) return false;
        return droppedAccepters == null || !droppedAccepters.contains(player);
    }

    public Set<UUID> eligibleAccepters() {
        Set<UUID> out = new LinkedHashSet<>(accepters);
        if (droppedAccepters != null) out.removeAll(droppedAccepters);
        return out;
    }

    /** Current objective based on conflictCount: index 0 = exposition, 1 = conflict 1, 2 = conflict 2 */
    public ObjectiveInstance getCurrentObjective() {
        if (conflictCount < objectives.size()) return objectives.get(conflictCount);
        return null;
    }

    public void claimReward(int conflictIndex) {
        rewardsClaimed.add(conflictIndex);
    }

    public boolean hasClaimedReward(int conflictIndex) {
        return rewardsClaimed.contains(conflictIndex);
    }

    public int getRewardXp() { return rewardXp; }
    public void setRewardXp(int rewardXp) { this.rewardXp = rewardXp; }

    public List<PhaseReward> getPhaseRewards() { return phaseRewards; }
    public void setPhaseRewards(List<PhaseReward> phaseRewards) { this.phaseRewards = phaseRewards; }

    /** Reward for the given phase index (0 = exposition, 1 = conflict 1, ...). Returns null if out of range. */
    public PhaseReward getPhaseReward(int index) {
        if (index < 0 || index >= phaseRewards.size()) return null;
        return phaseRewards.get(index);
    }

    /**
     * Per-phase rolled reward. Captured at quest generation and dispensed when
     * the player turns in the matching phase. The Gson-serialized
     * {@link com.chonbosmods.loot.Nat20LootData} in {@link #rewardItemDataJson} is
     * rehydrated at dispense so affix metadata round-trips onto the ItemStack.
     */
    public static class PhaseReward {
        private String rewardItemId;
        private int rewardItemCount;
        private String rewardItemDisplayName;
        private String rewardItemDataJson;

        public PhaseReward() {}

        public PhaseReward(String rewardItemId, int rewardItemCount,
                           String rewardItemDisplayName, String rewardItemDataJson) {
            this.rewardItemId = rewardItemId;
            this.rewardItemCount = rewardItemCount;
            this.rewardItemDisplayName = rewardItemDisplayName;
            this.rewardItemDataJson = rewardItemDataJson;
        }

        public String getRewardItemId() { return rewardItemId; }
        public void setRewardItemId(String rewardItemId) { this.rewardItemId = rewardItemId; }
        public int getRewardItemCount() { return rewardItemCount; }
        public void setRewardItemCount(int rewardItemCount) { this.rewardItemCount = rewardItemCount; }
        public String getRewardItemDisplayName() { return rewardItemDisplayName; }
        public void setRewardItemDisplayName(String rewardItemDisplayName) { this.rewardItemDisplayName = rewardItemDisplayName; }
        public String getRewardItemDataJson() { return rewardItemDataJson; }
        public void setRewardItemDataJson(String rewardItemDataJson) { this.rewardItemDataJson = rewardItemDataJson; }
    }
}
