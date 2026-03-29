package com.chonbosmods.settlement;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent data for a placed settlement.
 * Serialized/deserialized by Gson into settlements.json: no Hytale codecs.
 */
public class SettlementRecord {

    private String cellKey;
    private UUID worldUUID;
    private double posX;
    private double posY;
    private double posZ;
    private String type;
    private long placedAt;
    private List<NpcRecord> npcs = new ArrayList<>();

    /** No-arg constructor for Gson deserialization. */
    public SettlementRecord() {}

    /**
     * Convenience constructor: sets placedAt to current time automatically.
     */
    public SettlementRecord(String cellKey, UUID worldUUID,
                            double posX, double posY, double posZ,
                            SettlementType settlementType) {
        this.cellKey = cellKey;
        this.worldUUID = worldUUID;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.type = settlementType.name();
        this.placedAt = System.currentTimeMillis();
    }

    public String getCellKey() { return cellKey; }

    public UUID getWorldUUID() { return worldUUID; }

    public double getPosX() { return posX; }

    public double getPosY() { return posY; }

    public void setPosY(double posY) { this.posY = posY; }

    public double getPosZ() { return posZ; }

    public String getType() { return type; }

    public long getPlacedAt() { return placedAt; }

    public List<NpcRecord> getNpcs() { return npcs; }

    /**
     * Finds an NPC record by generated name, or null if not found.
     */
    public @Nullable NpcRecord getNpcByName(String generatedName) {
        for (NpcRecord npc : npcs) {
            if (generatedName.equals(npc.getGeneratedName())) return npc;
        }
        return null;
    }

    /**
     * Returns the SettlementType enum constant corresponding to the stored type string.
     */
    public SettlementType getSettlementType() {
        return SettlementType.valueOf(type);
    }
}
