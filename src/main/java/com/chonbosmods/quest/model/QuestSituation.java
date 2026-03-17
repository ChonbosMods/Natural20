package com.chonbosmods.quest.model;

import java.util.List;
import java.util.Map;

public class QuestSituation {
    private String id;
    private List<QuestVariant> expositionVariants;
    private List<QuestVariant> conflictVariants;
    private List<QuestVariant> resolutionVariants;
    private List<QuestReferenceTemplate> references;
    private Map<String, Double> npcRoleWeights;

    public QuestSituation() {}

    public QuestSituation(String id, List<QuestVariant> expositionVariants,
                           List<QuestVariant> conflictVariants,
                           List<QuestVariant> resolutionVariants,
                           List<QuestReferenceTemplate> references,
                           Map<String, Double> npcRoleWeights) {
        this.id = id;
        this.expositionVariants = expositionVariants;
        this.conflictVariants = conflictVariants;
        this.resolutionVariants = resolutionVariants;
        this.references = references;
        this.npcRoleWeights = npcRoleWeights;
    }

    public String getId() { return id; }
    public List<QuestVariant> getExpositionVariants() { return expositionVariants; }
    public List<QuestVariant> getConflictVariants() { return conflictVariants; }
    public List<QuestVariant> getResolutionVariants() { return resolutionVariants; }
    public List<QuestReferenceTemplate> getReferences() { return references; }
    public Map<String, Double> getNpcRoleWeights() { return npcRoleWeights; }
}
