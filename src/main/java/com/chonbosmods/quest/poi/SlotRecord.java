package com.chonbosmods.quest.poi;

import javax.annotation.Nullable;

/**
 * One mob in a {@link MobGroupRecord}. Serialized by Gson; mutable POJO.
 *
 * <p>{@code currentUuid} is ephemeral and refreshed every time the slot (re)spawns.
 * {@code isDead} is terminal: once true, the slot is never respawned.
 */
public class SlotRecord {

    private int slotIndex;
    private boolean isBoss;
    private boolean isDead;
    @Nullable private String currentUuid;

    /** No-arg constructor for Gson. */
    public SlotRecord() {}

    public SlotRecord(int slotIndex, boolean isBoss) {
        this.slotIndex = slotIndex;
        this.isBoss = isBoss;
        this.isDead = false;
        this.currentUuid = null;
    }

    public int getSlotIndex() { return slotIndex; }

    public boolean isBoss() { return isBoss; }

    public boolean isDead() { return isDead; }
    public void setDead(boolean dead) { this.isDead = dead; }

    public @Nullable String getCurrentUuid() { return currentUuid; }
    public void setCurrentUuid(@Nullable String currentUuid) { this.currentUuid = currentUuid; }
}
