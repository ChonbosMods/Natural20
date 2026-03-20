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
    private List<PhaseInstance> phases = new ArrayList<>();
    private int currentPhaseIndex;
    private Map<String, String> variableBindings = new HashMap<>();
    private Set<Integer> rewardsClaimed = new HashSet<>();

    public QuestInstance() {}

    public QuestInstance(String questId, String situationId, String sourceNpcId,
                         String sourceSettlementId, List<PhaseInstance> phases,
                         Map<String, String> variableBindings) {
        this.questId = questId;
        this.situationId = situationId;
        this.sourceNpcId = sourceNpcId;
        this.sourceSettlementId = sourceSettlementId;
        this.phases = phases;
        this.currentPhaseIndex = 0;
        this.variableBindings = variableBindings;
    }

    public String getQuestId() { return questId; }
    public String getSituationId() { return situationId; }
    public String getSourceNpcId() { return sourceNpcId; }
    public String getSourceSettlementId() { return sourceSettlementId; }
    public List<PhaseInstance> getPhases() { return phases; }
    public int getCurrentPhaseIndex() { return currentPhaseIndex; }
    public Map<String, String> getVariableBindings() { return variableBindings; }
    public Set<Integer> getRewardsClaimed() { return rewardsClaimed; }

    public PhaseInstance getCurrentPhase() {
        if (currentPhaseIndex < phases.size()) return phases.get(currentPhaseIndex);
        return null;
    }

    public boolean advancePhase() {
        if (currentPhaseIndex < phases.size() - 1) {
            currentPhaseIndex++;
            return true;
        }
        return false;
    }

    public boolean isComplete() {
        return currentPhaseIndex >= phases.size() - 1
            && phases.get(phases.size() - 1).isComplete();
    }

    public void claimReward(int phaseIndex) {
        rewardsClaimed.add(phaseIndex);
    }

    public boolean hasClaimedReward(int phaseIndex) {
        return rewardsClaimed.contains(phaseIndex);
    }
}
