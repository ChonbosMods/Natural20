package com.chonbosmods.quest.poi;

import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.progression.DifficultyTier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent per-player, per-quest, per-POI mob group data.
 * Serialized/deserialized by Gson into {@code mob_groups.json}: no Hytale codecs.
 *
 * <p>All rolled state (direction, championCount, affixes, bossName) is frozen at first-spawn
 * and replayed verbatim on chunk reload and server restart. Only {@code slots[i].isDead}
 * and {@code slots[i].currentUuid} mutate after creation.
 */
public class MobGroupRecord {

    private String groupKey;
    private String ownerPlayerUuid;
    private String questId;
    private int poiSlotIdx;
    private String mobRole;

    private double anchorX;
    private double anchorY;
    private double anchorZ;

    private long spawnGenerationId;
    private DifficultyTier groupDifficulty;
    private DifficultyTier bossDifficulty;
    private PoiGroupDirection direction;
    private int championCount;
    private String bossName;

    private List<RolledAffix> sharedChampionAffixes = new ArrayList<>();
    private List<RolledAffix> bossAffixes = new ArrayList<>();
    private List<SlotRecord> slots = new ArrayList<>();

    private long createdAtMillis;

    /** No-arg constructor for Gson. */
    public MobGroupRecord() {}

    public static String keyFor(UUID ownerPlayerUuid, String questId, int poiSlotIdx) {
        return "poi:" + ownerPlayerUuid + ":" + questId + ":" + poiSlotIdx;
    }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public String getOwnerPlayerUuid() { return ownerPlayerUuid; }
    public void setOwnerPlayerUuid(String ownerPlayerUuid) { this.ownerPlayerUuid = ownerPlayerUuid; }

    public String getQuestId() { return questId; }
    public void setQuestId(String questId) { this.questId = questId; }

    public int getPoiSlotIdx() { return poiSlotIdx; }
    public void setPoiSlotIdx(int poiSlotIdx) { this.poiSlotIdx = poiSlotIdx; }

    public String getMobRole() { return mobRole; }
    public void setMobRole(String mobRole) { this.mobRole = mobRole; }

    public double getAnchorX() { return anchorX; }
    public double getAnchorY() { return anchorY; }
    public double getAnchorZ() { return anchorZ; }
    public void setAnchor(double x, double y, double z) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
    }

    public long getSpawnGenerationId() { return spawnGenerationId; }
    public void setSpawnGenerationId(long spawnGenerationId) { this.spawnGenerationId = spawnGenerationId; }

    public DifficultyTier getGroupDifficulty() { return groupDifficulty; }
    public void setGroupDifficulty(DifficultyTier groupDifficulty) { this.groupDifficulty = groupDifficulty; }

    public DifficultyTier getBossDifficulty() { return bossDifficulty; }
    public void setBossDifficulty(DifficultyTier bossDifficulty) { this.bossDifficulty = bossDifficulty; }

    public PoiGroupDirection getDirection() { return direction; }
    public void setDirection(PoiGroupDirection direction) { this.direction = direction; }

    public int getChampionCount() { return championCount; }
    public void setChampionCount(int championCount) { this.championCount = championCount; }

    public String getBossName() { return bossName; }
    public void setBossName(String bossName) { this.bossName = bossName; }

    public List<RolledAffix> getSharedChampionAffixes() { return sharedChampionAffixes; }
    public void setSharedChampionAffixes(List<RolledAffix> affixes) {
        this.sharedChampionAffixes = affixes != null ? new ArrayList<>(affixes) : new ArrayList<>();
    }

    public List<RolledAffix> getBossAffixes() { return bossAffixes; }
    public void setBossAffixes(List<RolledAffix> affixes) {
        this.bossAffixes = affixes != null ? new ArrayList<>(affixes) : new ArrayList<>();
    }

    public List<SlotRecord> getSlots() { return slots; }
    public void setSlots(List<SlotRecord> slots) {
        this.slots = slots != null ? new ArrayList<>(slots) : new ArrayList<>();
    }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }
}
