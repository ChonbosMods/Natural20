package com.chonbosmods.settlement;

import com.chonbosmods.npc.Nat20PlaceNameGenerator;

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
    private String name;
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /**
     * Get the settlement's display name. Uses the stored name if present,
     * otherwise generates one deterministically from the cell key hash.
     */
    public String deriveName() {
        if (name != null && !name.isEmpty()) return name;
        // Generate deterministically from cell key so the same settlement always gets the same name
        return Nat20PlaceNameGenerator.generate(cellKey.hashCode());
    }
}
