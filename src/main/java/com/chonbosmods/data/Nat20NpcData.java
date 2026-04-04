package com.chonbosmods.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;

public class Nat20NpcData implements Component<EntityStore> {

    public enum QuestMarkerState {
        NONE,
        QUEST_AVAILABLE,
        QUEST_TURN_IN
    }

    public static final BuilderCodec<Nat20NpcData> CODEC = BuilderCodec.builder(Nat20NpcData.class, Nat20NpcData::new)
            .addField(new KeyedCodec<>("GeneratedName", Codec.STRING), Nat20NpcData::setGeneratedName, Nat20NpcData::getGeneratedName)
            .addField(new KeyedCodec<>("RoleName", Codec.STRING), Nat20NpcData::setRoleName, Nat20NpcData::getRoleName)
            .addField(new KeyedCodec<>("Disposition", Codec.INTEGER), Nat20NpcData::setDefaultDisposition, Nat20NpcData::getDefaultDisposition)
            .addField(new KeyedCodec<>("DialogueState", Codec.STRING), Nat20NpcData::setDialogueState, Nat20NpcData::getDialogueState)
            .addField(new KeyedCodec<>("Flags", MapCodec.STRING_HASH_MAP_CODEC), Nat20NpcData::setFlags, Nat20NpcData::getFlags)
            .addField(new KeyedCodec<>("SettlementCellKey", Codec.STRING), Nat20NpcData::setSettlementCellKey, Nat20NpcData::getSettlementCellKey)
            .build();

    private String generatedName;
    private String roleName;
    private int defaultDisposition;
    private String dialogueState;
    private Map<String, String> flags = new HashMap<>();
    private String settlementCellKey;
    private QuestMarkerState questMarkerState = QuestMarkerState.NONE;

    public Nat20NpcData() {
    }

    public String getGeneratedName() {
        return generatedName;
    }

    public void setGeneratedName(String generatedName) {
        this.generatedName = generatedName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public int getDefaultDisposition() {
        return defaultDisposition;
    }

    public void setDefaultDisposition(int defaultDisposition) {
        this.defaultDisposition = defaultDisposition;
    }

    public String getDialogueState() {
        return dialogueState;
    }

    public void setDialogueState(String dialogueState) {
        this.dialogueState = dialogueState;
    }

    public Map<String, String> getFlags() {
        return flags;
    }

    public void setFlags(Map<String, String> flags) {
        this.flags = flags != null ? new HashMap<>(flags) : new HashMap<>();
    }

    public String getSettlementCellKey() {
        return settlementCellKey;
    }

    public void setSettlementCellKey(String settlementCellKey) {
        this.settlementCellKey = settlementCellKey;
    }

    public QuestMarkerState getQuestMarkerState() {
        return questMarkerState;
    }

    public void setQuestMarkerState(QuestMarkerState questMarkerState) {
        this.questMarkerState = questMarkerState;
    }

    @Override
    public Nat20NpcData clone() {
        Nat20NpcData copy = new Nat20NpcData();
        copy.generatedName = this.generatedName;
        copy.roleName = this.roleName;
        copy.defaultDisposition = this.defaultDisposition;
        copy.dialogueState = this.dialogueState;
        copy.flags = new HashMap<>(this.flags);
        copy.settlementCellKey = this.settlementCellKey;
        copy.questMarkerState = this.questMarkerState;
        return copy;
    }
}
