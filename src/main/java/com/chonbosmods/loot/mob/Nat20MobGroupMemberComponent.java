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
 *
 * <p>Also carries two persistent behavior-state fields consumed by
 * {@code Nat20MobGroupLeashSystem}: {@code lastCombatMillis} (wall-clock timestamp of the
 * most recent damage event involving this mob, written by {@code Nat20MobGroupCombatStampSystem})
 * and {@code outOfRangeTicks} (consecutive out-of-range leash-tick counter). Putting these
 * on the component means they survive chunk unload/reload and server restart, unlike the
 * leash system's old ephemeral {@code ConcurrentHashMap} keyed by {@code Ref} which was
 * wiped on every chunk flap.
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
                    .addField(new KeyedCodec<>("LastCombatMillis", Codec.LONG),
                            Nat20MobGroupMemberComponent::setLastCombatMillis,
                            Nat20MobGroupMemberComponent::getLastCombatMillis)
                    .addField(new KeyedCodec<>("OutOfRangeTicks", Codec.INTEGER),
                            Nat20MobGroupMemberComponent::setOutOfRangeTicks,
                            Nat20MobGroupMemberComponent::getOutOfRangeTicks)
                    .build();

    private String groupKey = "";
    private int slotIndex = -1;
    private long lastCombatMillis = 0L;
    private int outOfRangeTicks = 0;

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

    public long getLastCombatMillis() {
        return lastCombatMillis;
    }

    public void setLastCombatMillis(long lastCombatMillis) {
        this.lastCombatMillis = lastCombatMillis;
    }

    public int getOutOfRangeTicks() {
        return outOfRangeTicks;
    }

    public void setOutOfRangeTicks(int outOfRangeTicks) {
        this.outOfRangeTicks = outOfRangeTicks;
    }

    @Override
    public Nat20MobGroupMemberComponent clone() {
        Nat20MobGroupMemberComponent c = new Nat20MobGroupMemberComponent(this.groupKey, this.slotIndex);
        c.lastCombatMillis = this.lastCombatMillis;
        c.outOfRangeTicks = this.outOfRangeTicks;
        return c;
    }
}
