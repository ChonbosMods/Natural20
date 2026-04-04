package com.chonbosmods.quest;

public class ObjectiveInstance {
    private ObjectiveType type;
    private String targetId;
    private String targetLabel;
    private String targetLabelPlural;
    private int requiredCount;
    private int currentCount;
    private boolean complete;
    private String directionHint;
    private String locationId;
    private String targetLabelPlural;

    public ObjectiveInstance() {}

    public ObjectiveInstance(ObjectiveType type, String targetId, String targetLabel,
                             int requiredCount, String directionHint, String locationId) {
        this.type = type;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.requiredCount = requiredCount;
        this.currentCount = 0;
        this.complete = false;
        this.directionHint = directionHint;
        this.locationId = locationId;
    }

    public ObjectiveType getType() { return type; }
    public String getTargetId() { return targetId; }
    public String getTargetLabel() { return targetLabel; }
    public String getTargetLabelPlural() { return targetLabelPlural; }
    public void setTargetLabelPlural(String targetLabelPlural) { this.targetLabelPlural = targetLabelPlural; }
    public String getEffectiveLabel() { return requiredCount != 1 && targetLabelPlural != null ? targetLabelPlural : targetLabel; }
    public int getRequiredCount() { return requiredCount; }
    public int getCurrentCount() { return currentCount; }
    public boolean isComplete() { return complete; }
    public String getDirectionHint() { return directionHint; }
    public String getLocationId() { return locationId; }
    public String getTargetLabelPlural() { return targetLabelPlural; }
    public void setTargetLabelPlural(String targetLabelPlural) { this.targetLabelPlural = targetLabelPlural; }
    public String getEffectiveLabel() { return requiredCount != 1 && targetLabelPlural != null ? targetLabelPlural : targetLabel; }

    public void incrementProgress(int amount) {
        currentCount = Math.min(currentCount + amount, requiredCount);
        if (currentCount >= requiredCount) complete = true;
    }

    public void markComplete() {
        currentCount = requiredCount;
        complete = true;
    }

    public void setCurrentCount(int count) {
        this.currentCount = count;
    }

    public void uncomplete() {
        this.complete = false;
        this.currentCount = Math.min(this.currentCount, this.requiredCount - 1);
    }
}
