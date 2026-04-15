package com.chonbosmods.progression;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.function.FunctionCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Per-mob scaling state. Written once by {@link Nat20MobScaleSystem} on first
 * {@code onEntityAdded(SPAWN|LOAD)}; re-read on chunk reload (idempotent via
 * {@code scaled}). Carries area_level and tier so the loot pipeline can read
 * {@code ilvl = areaLevel} at death time.
 */
public class Nat20MobLevel implements Component<EntityStore> {

    private static final FunctionCodec<String, Tier> TIER_CODEC =
            new FunctionCodec<>(Codec.STRING, Tier::valueOf, Tier::name);

    public static final BuilderCodec<Nat20MobLevel> CODEC = BuilderCodec.builder(Nat20MobLevel.class, Nat20MobLevel::new)
            .addField(new KeyedCodec<>("AreaLevel", Codec.INTEGER), Nat20MobLevel::setAreaLevel, Nat20MobLevel::getAreaLevel)
            .addField(new KeyedCodec<>("Tier", TIER_CODEC), Nat20MobLevel::setTier, Nat20MobLevel::getTier)
            .addField(new KeyedCodec<>("Scaled", Codec.INTEGER), Nat20MobLevel::setScaledFlag, Nat20MobLevel::getScaledFlag)
            .build();

    private int areaLevel = 1;
    private Tier tier = Tier.REGULAR;
    private int scaledFlag = 0;

    public Nat20MobLevel() {}

    public int getAreaLevel() { return areaLevel; }
    public void setAreaLevel(int areaLevel) { this.areaLevel = areaLevel; }

    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = (tier == null) ? Tier.REGULAR : tier; }

    public boolean isScaled() { return scaledFlag != 0; }
    public void setScaled(boolean scaled) { this.scaledFlag = scaled ? 1 : 0; }
    public int getScaledFlag() { return scaledFlag; }
    public void setScaledFlag(int flag) { this.scaledFlag = flag; }

    @Override
    public Nat20MobLevel clone() {
        Nat20MobLevel copy = new Nat20MobLevel();
        copy.areaLevel = this.areaLevel;
        copy.tier = this.tier;
        copy.scaledFlag = this.scaledFlag;
        return copy;
    }
}
