package com.chonbosmods.quest;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class PhaseInstance {
    private PhaseType type;
    private String variantId;
    private List<ObjectiveInstance> objectives = new ArrayList<>();
    private @Nullable String referenceId;

    public PhaseInstance() {}

    public PhaseInstance(PhaseType type, String variantId, List<ObjectiveInstance> objectives,
                         @Nullable String referenceId) {
        this.type = type;
        this.variantId = variantId;
        this.objectives = objectives;
        this.referenceId = referenceId;
    }

    public PhaseType getType() { return type; }
    public String getVariantId() { return variantId; }
    public List<ObjectiveInstance> getObjectives() { return objectives; }
    public @Nullable String getReferenceId() { return referenceId; }

    public boolean isComplete() {
        return objectives.stream().allMatch(ObjectiveInstance::isComplete);
    }
}
