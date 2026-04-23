package com.chonbosmods.progression;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.function.FunctionCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Per-mob scaling state. Written once by {@link Nat20MobScaleSystem} on first
 * {@code onEntityAdded(SPAWN|LOAD)}; re-read on chunk reload (idempotent via
 * {@code scaled}). Carries area_level and tier so the loot pipeline can read
 * {@code ilvl = areaLevel} at death time.
 */
public class Nat20MobLevel implements Component<EntityStore> {

    private static final FunctionCodec<String, Tier> TIER_CODEC =
            new FunctionCodec<>(Codec.STRING, Tier::valueOf, Tier::name);

    private static final FunctionCodec<String, DifficultyTier> DIFFICULTY_CODEC =
            new FunctionCodec<>(Codec.STRING, DifficultyTier::fromName,
                    d -> d == null ? "" : d.name());

    public static final BuilderCodec<Nat20MobLevel> CODEC = BuilderCodec.builder(Nat20MobLevel.class, Nat20MobLevel::new)
            .addField(new KeyedCodec<>("AreaLevel", Codec.INTEGER), Nat20MobLevel::setAreaLevel, Nat20MobLevel::getAreaLevel)
            .addField(new KeyedCodec<>("Tier", TIER_CODEC), Nat20MobLevel::setTier, Nat20MobLevel::getTier)
            .addField(new KeyedCodec<>("Scaled", Codec.INTEGER), Nat20MobLevel::setScaledFlag, Nat20MobLevel::getScaledFlag)
            .addField(new KeyedCodec<>("Difficulty", DIFFICULTY_CODEC),
                    Nat20MobLevel::setDifficultyTier, Nat20MobLevel::getDifficultyTier)
            .addField(new KeyedCodec<>("PartyBump", Codec.INTEGER),
                    Nat20MobLevel::setPartyBump, Nat20MobLevel::getPartyBump)
            .build();

    private int areaLevel = 1;
    private Tier tier = Tier.REGULAR;
    private int scaledFlag = 0;
    @Nullable private DifficultyTier difficultyTier = null;
    private int partyBump = 0;

    public Nat20MobLevel() {}

    public int getAreaLevel() { return areaLevel; }
    public void setAreaLevel(int areaLevel) { this.areaLevel = areaLevel; }

    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = (tier == null) ? Tier.REGULAR : tier; }

    public boolean isScaled() { return scaledFlag != 0; }
    public void setScaled(boolean scaled) { this.scaledFlag = scaled ? 1 : 0; }
    public int getScaledFlag() { return scaledFlag; }
    public void setScaledFlag(int flag) { this.scaledFlag = flag; }

    @Nullable public DifficultyTier getDifficultyTier() { return difficultyTier; }
    public void setDifficultyTier(@Nullable DifficultyTier tier) { this.difficultyTier = tier; }

    public int getPartyBump() { return partyBump; }
    public void setPartyBump(int partyBump) { this.partyBump = Math.max(0, partyBump); }

    @Override
    public Nat20MobLevel clone() {
        Nat20MobLevel copy = new Nat20MobLevel();
        copy.areaLevel = this.areaLevel;
        copy.tier = this.tier;
        copy.scaledFlag = this.scaledFlag;
        copy.difficultyTier = this.difficultyTier;
        copy.partyBump = this.partyBump;
        return copy;
    }
}
