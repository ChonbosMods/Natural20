package com.chonbosmods.quest;

import java.util.List;
import javax.annotation.Nullable;

public class ReferenceState {
    private String referenceId;
    private String templateId;
    private ReferenceTier tier;
    private String boundNpcId;
    private String boundSettlementId;
    private List<String> boundSituations;
    private @Nullable String unlockedTopicId;

    public ReferenceState() {}

    public ReferenceState(String referenceId, String templateId, ReferenceTier tier,
                          String boundNpcId, String boundSettlementId,
                          List<String> boundSituations) {
        this.referenceId = referenceId;
        this.templateId = templateId;
        this.tier = tier;
        this.boundNpcId = boundNpcId;
        this.boundSettlementId = boundSettlementId;
        this.boundSituations = boundSituations;
    }

    public String getReferenceId() { return referenceId; }
    public String getTemplateId() { return templateId; }
    public ReferenceTier getTier() { return tier; }
    public String getBoundNpcId() { return boundNpcId; }
    public String getBoundSettlementId() { return boundSettlementId; }
    public List<String> getBoundSituations() { return boundSituations; }
    public @Nullable String getUnlockedTopicId() { return unlockedTopicId; }

    public void setTier(ReferenceTier tier) { this.tier = tier; }
    public void setUnlockedTopicId(String topicId) { this.unlockedTopicId = topicId; }
}
