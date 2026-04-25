package com.chonbosmods.quest;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
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
    // As of the 2026-04-22 Option B pivot this field is never populated: accepters
    // stay on the quest for subsequent phases and simply miss individual phases
    // via {@link #phaseMissedAccepters}. Retained for legacy JSON back-compat.
    private Set<UUID> droppedAccepters = new HashSet<>();

    /** Per-phase missed-accepter tracking. Indexed by phase number (conflictCount).
     *  Populated by the proximity gate at phase-completion: accepters who were out
     *  of range or offline at that moment are recorded here so per-phase reward
     *  dispense (both XP in the tracking systems and item in CONTINUE_QUEST) can
     *  skip them. They remain on the quest for all other phases. See the 2026-04-22
     *  Option B pivot: "miss a phase while out-of-range, stay on the quest for
     *  subsequent phases." */
    private List<Set<UUID>> phaseMissedAccepters = new ArrayList<>();

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

    /**
     * Per-player pre-rolled reward cache. Populated when the turn-in dialogue
     * session is built in DialogueManager.injectQuestTurnInTopics so {@code
     * {reward_item}} can substitute to the rolled item's display name in the
     * resolution text. Consumed by DialogueActionRegistry.dispensePhaseReward
     * to dispense the same item the player saw in the dialogue.
     *
     * <p>Transient: not serialized. If a dialogue session opens, the player
     * walks away, and reopens, the cache is overwritten by the new roll.
     *
     * <p>Outer key = phaseIndex; inner key = player UUID.
     */
    private transient Map<Integer, Map<UUID, ItemStack>> preRolledRewards = new HashMap<>();

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
        if (!getAccepters().contains(player)) return false;
        return droppedAccepters == null || !droppedAccepters.contains(player);
    }

    public Set<UUID> eligibleAccepters() {
        Set<UUID> out = new LinkedHashSet<>(getAccepters());
        if (droppedAccepters != null) out.removeAll(droppedAccepters);
        return out;
    }

    /** Record the given accepters as missing phase {@code phaseIndex}. Extends
     *  the backing list with empty sets as needed to cover the index. Idempotent
     *  (adds to existing set). */
    public void markMissedForPhase(int phaseIndex, Set<UUID> missed) {
        if (missed == null || missed.isEmpty()) return;
        if (phaseMissedAccepters == null) phaseMissedAccepters = new ArrayList<>();
        while (phaseMissedAccepters.size() <= phaseIndex) {
            phaseMissedAccepters.add(new HashSet<>());
        }
        Set<UUID> bucket = phaseMissedAccepters.get(phaseIndex);
        if (bucket == null) {
            bucket = new HashSet<>();
            phaseMissedAccepters.set(phaseIndex, bucket);
        }
        bucket.addAll(missed);
    }

    /** Snapshot of accepters who missed phase {@code phaseIndex}. Returns an
     *  empty set for phases that have not been recorded yet (index past tail,
     *  or null bucket from legacy deserialization). */
    public Set<UUID> getMissedForPhase(int phaseIndex) {
        if (phaseMissedAccepters == null) return Collections.emptySet();
        if (phaseIndex < 0 || phaseIndex >= phaseMissedAccepters.size()) {
            return Collections.emptySet();
        }
        Set<UUID> bucket = phaseMissedAccepters.get(phaseIndex);
        if (bucket == null) return Collections.emptySet();
        return bucket;
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
     * Cache a pre-rolled reward for the given phase + player. Overwrites any
     * prior entry for that key. Called from DialogueManager when the turn-in
     * topic is built.
     */
    public void cachePreRolledReward(int phaseIndex, UUID playerUuid, ItemStack stack) {
        if (preRolledRewards == null) preRolledRewards = new HashMap<>();
        preRolledRewards.computeIfAbsent(phaseIndex, k -> new HashMap<>()).put(playerUuid, stack);
    }

    /**
     * Consume (read + remove) the pre-rolled reward for this phase + player.
     * Returns null if no cached entry exists. Called from
     * {@code DialogueActionRegistry.dispensePhaseReward}.
     */
    public ItemStack consumePreRolledReward(int phaseIndex, UUID playerUuid) {
        if (preRolledRewards == null) return null;
        Map<UUID, ItemStack> phaseMap = preRolledRewards.get(phaseIndex);
        if (phaseMap == null) return null;
        return phaseMap.remove(playerUuid);
    }

    /**
     * Per-phase reward inputs, captured at quest generation. The actual item is rolled
     * at phase turn-in via {@link com.chonbosmods.quest.AffixRewardRoller#roll}, using a per-player ilvl
     * derived from {@link com.chonbosmods.quest.QuestRewardIlvl#reward}.
     *
     * <p>Stores only the inputs needed at dispense time:
     * <ul>
     *   <li>{@code rewardTier}: rolled at quest gen with the dampener applied.
     *       Reused per accepter so all party members share the same rarity tier
     *       (different items, same rarity).</li>
     *   <li>{@code areaLevelAtSpawn}: snapshotted at quest gen. Used as the area
     *       baseline in the per-player reward formula.</li>
     *   <li>{@code ilvlBonus}: copy of difficulty's ilvlBonus (Easy=0, Medium=2,
     *       Hard=5). Constant across all phases of a single quest.</li>
     * </ul>
     *
     * <p>See design: {@code docs/plans/2026-04-25-quest-reward-encounter-scaling-design.md}.
     */
    public static class PhaseReward {
        private String rewardTier;
        private int areaLevelAtSpawn;
        private int ilvlBonus;

        public PhaseReward() {}

        public PhaseReward(String rewardTier, int areaLevelAtSpawn, int ilvlBonus) {
            this.rewardTier = rewardTier;
            this.areaLevelAtSpawn = areaLevelAtSpawn;
            this.ilvlBonus = ilvlBonus;
        }

        public String getRewardTier() { return rewardTier; }
        public void setRewardTier(String rewardTier) { this.rewardTier = rewardTier; }
        public int getAreaLevelAtSpawn() { return areaLevelAtSpawn; }
        public void setAreaLevelAtSpawn(int areaLevelAtSpawn) { this.areaLevelAtSpawn = areaLevelAtSpawn; }
        public int getIlvlBonus() { return ilvlBonus; }
        public void setIlvlBonus(int ilvlBonus) { this.ilvlBonus = ilvlBonus; }
    }
}
