package com.chonbosmods.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** XP awarded on final turn-in. Sourced from {@link com.chonbosmods.quest.model.DifficultyConfig#xpAmount()}.
     *  Stored even when no XP system is wired so the value survives until one lands. */
    private int rewardXp;
    /** Unique item id of the rolled reward, produced by {@link AffixRewardRoller}.
     *  Used at turn-in to construct the dispensed {@link com.hypixel.hytale.server.core.inventory.ItemStack}.
     *  Primitive storage only: the full {@link com.chonbosmods.loot.Nat20LootData} payload lives in
     *  {@link #rewardItemDataJson} so affix metadata round-trips cleanly through Gson. */
    private String rewardItemId;
    /** Reward stack quantity (always 1 for quest rewards today; field exists so future tunables don't
     *  require another schema bump). */
    private int rewardItemCount;
    /** Cached display name of the rolled reward. The dialogue {reward_item} binding reads this directly
     *  so no item lookup is needed at render time. */
    private String rewardItemDisplayName;
    /** Gson-serialized {@link com.chonbosmods.loot.Nat20LootData} for the rolled reward. Stored as a
     *  primitive String because Nat20LootData's BSON metadata format does not round-trip cleanly through
     *  the QuestInstance Gson serializer; the codec serializes affixes/gems to raw strings, so the entire
     *  object is Gson-friendly when stored as a JSON string. Reconstructed and reattached to the
     *  ItemStack at dispense time so combat systems see the affix payload. */
    private String rewardItemDataJson;

    /** True once the completion banner has fired for the current phase.
     *  Reset to false on phase advance (incrementConflictCount).
     *  Prevents re-firing when revertable objectives (FETCH_ITEM, COLLECT_RESOURCES)
     *  oscillate between ACTIVE_OBJECTIVE and READY_FOR_TURN_IN. */
    private boolean bannerShownForCurrentPhase;

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

    public String getRewardItemId() { return rewardItemId; }
    public void setRewardItemId(String rewardItemId) { this.rewardItemId = rewardItemId; }

    public int getRewardItemCount() { return rewardItemCount; }
    public void setRewardItemCount(int rewardItemCount) { this.rewardItemCount = rewardItemCount; }

    public String getRewardItemDisplayName() { return rewardItemDisplayName; }
    public void setRewardItemDisplayName(String rewardItemDisplayName) { this.rewardItemDisplayName = rewardItemDisplayName; }

    public String getRewardItemDataJson() { return rewardItemDataJson; }
    public void setRewardItemDataJson(String rewardItemDataJson) { this.rewardItemDataJson = rewardItemDataJson; }
}
