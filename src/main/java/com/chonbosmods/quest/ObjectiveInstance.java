package com.chonbosmods.quest;

import com.chonbosmods.quest.poi.PoiGroupDirection;

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

    // COLLECT_RESOURCES scaling (Option B from the tier-naming design):
    // baseRoll is the uniform roll in [countMin, countMax] taken at pre-gen time.
    // bonusPerZone is the per-tier additive rolled from Nat20XpMath.BONUS_RANGE_PER_TIER.
    // requiredCount is recomputed at GIVE_QUEST acceptance as:
    //   baseRoll + bonusPerZone * (playerZone - 1)
    // Both default to 0 for non-COLLECT objectives and for legacy saves; a zero baseRoll
    // means "no scaling data" and downstream code falls back to requiredCount.
    private int baseRoll;
    private int bonusPerZone;

    // When set, POIGroupSpawnCoordinator.firstSpawn uses this direction instead of
    // defaulting to KILL_COUNT. Set by QuestGenerator.applyBossPreRoll for KILL_BOSS
    // objectives so the coordinator reuses the pre-rolled boss metadata verbatim.
    private PoiGroupDirection forcedPoiDirection;

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
    public void setRequiredCount(int requiredCount) { this.requiredCount = requiredCount; }
    public int getBaseRoll() { return baseRoll; }
    public void setBaseRoll(int baseRoll) { this.baseRoll = baseRoll; }
    public int getBonusPerZone() { return bonusPerZone; }
    public void setBonusPerZone(int bonusPerZone) { this.bonusPerZone = bonusPerZone; }
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

    public PoiGroupDirection getForcedPoiDirection() { return forcedPoiDirection; }
    public void setForcedPoiDirection(PoiGroupDirection forcedPoiDirection) {
        this.forcedPoiDirection = forcedPoiDirection;
    }

    /** True when UI summaries should render as "kill &lt;boss&gt;" without a numeric count.
     *  The KILL_BOSS branch covers first-class boss objectives; the KILL_MOBS+forced
     *  branch keeps legacy templates (pre-Phase-1 migration) rendering correctly. */
    public boolean isSingletonBossKill() {
        return type == ObjectiveType.KILL_BOSS
            || (type == ObjectiveType.KILL_MOBS
                && forcedPoiDirection == PoiGroupDirection.KILL_BOSS);
    }
}
