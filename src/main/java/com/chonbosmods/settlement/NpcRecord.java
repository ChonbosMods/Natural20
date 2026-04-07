package com.chonbosmods.settlement;

import com.chonbosmods.quest.QuestInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent data for a single NPC within a settlement.
 * Serialized/deserialized by Gson: no Hytale codecs.
 */
public class NpcRecord {

    private String role;
    private UUID entityUUID;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float rotX;
    private float rotY;
    private float rotZ;
    private double leashRadius;
    private String generatedName;
    private int disposition;
    private String dialogueState;
    private Map<String, String> flags = new HashMap<>();
    private QuestInstance preGeneratedQuest;
    private String markerState;

    /** No-arg constructor for Gson deserialization. */
    public NpcRecord() {}

    public NpcRecord(String role, UUID entityUUID,
                     double spawnX, double spawnY, double spawnZ,
                     float rotX, float rotY, float rotZ,
                     double leashRadius, String generatedName) {
        this.role = role;
        this.entityUUID = entityUUID;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.leashRadius = leashRadius;
        this.generatedName = generatedName;
    }

    public String getRole() { return role; }

    /**
     * Display name for an NPC role string. Centralized so quest, smalltalk,
     * and any other dialogue surface render roles consistently.
     */
    public static String displayRole(String role) {
        if (role == null) return "";
        return switch (role) {
            case "ArtisanBlacksmith" -> "blacksmith";
            case "ArtisanAlchemist" -> "alchemist";
            case "ArtisanCook" -> "cook";
            case "TavernKeeper" -> "tavern keeper";
            default -> role.toLowerCase();
        };
    }

    /** Convenience: this NPC's role as a display string. */
    public String getDisplayRole() { return displayRole(role); }

    public UUID getEntityUUID() { return entityUUID; }

    public void setEntityUUID(UUID entityUUID) { this.entityUUID = entityUUID; }

    public double getSpawnX() { return spawnX; }

    public double getSpawnY() { return spawnY; }

    public double getSpawnZ() { return spawnZ; }

    public float getRotX() { return rotX; }

    public float getRotY() { return rotY; }

    public float getRotZ() { return rotZ; }

    public double getLeashRadius() { return leashRadius; }

    public String getGeneratedName() { return generatedName; }

    public void setGeneratedName(String generatedName) { this.generatedName = generatedName; }

    public int getDisposition() { return disposition; }
    public void setDisposition(int disposition) { this.disposition = disposition; }

    public String getDialogueState() { return dialogueState; }
    public void setDialogueState(String dialogueState) { this.dialogueState = dialogueState; }

    public Map<String, String> getFlags() { return flags; }
    public void setFlags(Map<String, String> flags) { this.flags = flags != null ? new HashMap<>(flags) : new HashMap<>(); }

    public QuestInstance getPreGeneratedQuest() { return preGeneratedQuest; }
    public void setPreGeneratedQuest(QuestInstance quest) { this.preGeneratedQuest = quest; }

    /**
     * Persisted marker state: "QUEST_AVAILABLE", "QUEST_TURN_IN", or null (NONE).
     * Survives entity UUID changes on chunk reload/NPC respawn.
     */
    public String getMarkerState() { return markerState; }
    public void setMarkerState(String markerState) { this.markerState = markerState; }
}
