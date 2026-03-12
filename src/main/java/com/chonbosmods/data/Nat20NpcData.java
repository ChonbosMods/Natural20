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

    public static final BuilderCodec<Nat20NpcData> CODEC = BuilderCodec.builder(Nat20NpcData.class, Nat20NpcData::new)
            .addField(new KeyedCodec<>("GeneratedName", Codec.STRING), Nat20NpcData::setGeneratedName, Nat20NpcData::getGeneratedName)
            .addField(new KeyedCodec<>("RoleName", Codec.STRING), Nat20NpcData::setRoleName, Nat20NpcData::getRoleName)
            .addField(new KeyedCodec<>("Disposition", Codec.INTEGER), Nat20NpcData::setDisposition, Nat20NpcData::getDisposition)
            .addField(new KeyedCodec<>("DialogueState", Codec.STRING), Nat20NpcData::setDialogueState, Nat20NpcData::getDialogueState)
            .addField(new KeyedCodec<>("Flags", MapCodec.STRING_HASH_MAP_CODEC), Nat20NpcData::setFlags, Nat20NpcData::getFlags)
            .build();

    private String generatedName;
    private String roleName;
    private int disposition;
    private String dialogueState;
    private Map<String, String> flags = new HashMap<>();

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

    public int getDisposition() {
        return disposition;
    }

    public void setDisposition(int disposition) {
        this.disposition = disposition;
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

    @Override
    public Nat20NpcData clone() {
        Nat20NpcData copy = new Nat20NpcData();
        copy.generatedName = this.generatedName;
        copy.roleName = this.roleName;
        copy.disposition = this.disposition;
        copy.dialogueState = this.dialogueState;
        copy.flags = new HashMap<>(this.flags);
        return copy;
    }
}
