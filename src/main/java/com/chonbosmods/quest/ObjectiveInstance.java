package com.chonbosmods.quest;

public class ObjectiveInstance {
    private ObjectiveType type;
    private String targetId;
    private String targetLabel;
    private String targetLabelPlural;
    private String targetEpithet;
    private int requiredCount;
    private int currentCount;
    private boolean complete;
    private String directionHint;
    private String locationId;

    // Pre-claimed POI data (set at quest generation for KILL_MOBS/FETCH_ITEM objectives)
    private boolean hasPoi;
    private int poiCenterX;
    private int poiCenterY;
    private int poiCenterZ;
    private String populationSpec;

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
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getTargetLabel() { return targetLabel; }
    public void setTargetLabel(String targetLabel) { this.targetLabel = targetLabel; }
    public String getTargetLabelPlural() { return targetLabelPlural; }
    public void setTargetLabelPlural(String targetLabelPlural) { this.targetLabelPlural = targetLabelPlural; }
    public String getTargetEpithet() { return targetEpithet; }
    public void setTargetEpithet(String targetEpithet) { this.targetEpithet = targetEpithet; }
    public String getLocationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }
    public String getEffectiveLabel() { return requiredCount != 1 && targetLabelPlural != null ? targetLabelPlural : targetLabel; }
    public int getRequiredCount() { return requiredCount; }
    public int getCurrentCount() { return currentCount; }
    public boolean isComplete() { return complete; }
    public String getDirectionHint() { return directionHint; }

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

    // POI accessors
    public boolean hasPoi() { return hasPoi; }
    public int getPoiCenterX() { return poiCenterX; }
    public int getPoiCenterY() { return poiCenterY; }
    public int getPoiCenterZ() { return poiCenterZ; }
    public String getPopulationSpec() { return populationSpec; }

    public void setPoi(int cx, int cy, int cz, String populationSpec) {
        this.hasPoi = true;
        this.poiCenterX = cx;
        this.poiCenterY = cy;
        this.poiCenterZ = cz;
        this.populationSpec = populationSpec;
    }
}
