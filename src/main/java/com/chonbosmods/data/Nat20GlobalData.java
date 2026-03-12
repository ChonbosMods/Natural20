package com.chonbosmods.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class Nat20GlobalData implements Component<EntityStore> {

    public static final BuilderCodec<Nat20GlobalData> CODEC = BuilderCodec.builder(Nat20GlobalData.class, Nat20GlobalData::new)
            .addField(new KeyedCodec<>("TotalNpcsSpawned", Codec.INTEGER), Nat20GlobalData::setTotalNpcsSpawned, Nat20GlobalData::getTotalNpcsSpawned)
            .build();

    private int totalNpcsSpawned;

    public Nat20GlobalData() {
    }

    public int getTotalNpcsSpawned() {
        return totalNpcsSpawned;
    }

    public void setTotalNpcsSpawned(int totalNpcsSpawned) {
        this.totalNpcsSpawned = totalNpcsSpawned;
    }

    @Override
    public Nat20GlobalData clone() {
        Nat20GlobalData copy = new Nat20GlobalData();
        copy.totalNpcsSpawned = this.totalNpcsSpawned;
        return copy;
    }
}
