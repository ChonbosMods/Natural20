package com.chonbosmods.loot.mob;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Stable group-membership tag attached to every mob spawned by {@code Nat20MobGroupSpawner}.
 * Reconciliation, kill dispatch, and double-spawn defense key off {@code (groupKey, slotIndex)}
 * rather than entity UUID, since UUIDs rewrite on chunk reload when the engine revives a
 * natively-persisted entity.
 */
public class Nat20MobGroupMemberComponent implements Component<EntityStore> {

    public static final BuilderCodec<Nat20MobGroupMemberComponent> CODEC =
            BuilderCodec.builder(Nat20MobGroupMemberComponent.class, Nat20MobGroupMemberComponent::new)
                    .addField(new KeyedCodec<>("GroupKey", Codec.STRING),
                            Nat20MobGroupMemberComponent::setGroupKey,
                            Nat20MobGroupMemberComponent::getGroupKey)
                    .addField(new KeyedCodec<>("SlotIndex", Codec.INTEGER),
                            Nat20MobGroupMemberComponent::setSlotIndex,
                            Nat20MobGroupMemberComponent::getSlotIndex)
                    .build();

    private String groupKey = "";
    private int slotIndex = -1;

    public Nat20MobGroupMemberComponent() {}

    public Nat20MobGroupMemberComponent(String groupKey, int slotIndex) {
        this.groupKey = groupKey;
        this.slotIndex = slotIndex;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    @Override
    public Nat20MobGroupMemberComponent clone() {
        return new Nat20MobGroupMemberComponent(this.groupKey, this.slotIndex);
    }
}
