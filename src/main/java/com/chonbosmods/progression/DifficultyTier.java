package com.chonbosmods.progression;

import javax.annotation.Nullable;

/**
 * Rolled once per Nat20 spawn group. Drives mlvl modifier, entity tint,
 * name pool selection (bosses only), and affix counts (combined with {@link Tier}).
 * Only Nat20 spawn pathways assign this; native Hytale spawns stay untagged here.
 */
public enum DifficultyTier {
    UNCOMMON("#3e9049",  "Nat20DifficultyUncommonTint",  1, "uncommon"),
    RARE    ("#2770b7",  "Nat20DifficultyRareTint",      2, "rare"),
    EPIC    ("#8b339e",  "Nat20DifficultyEpicTint",      3, "epic"),
    LEGENDARY("#bb8a2c", "Nat20DifficultyLegendaryTint", 5, "legendary");

    private final String tintHex;
    private final String tintEffectId;
    private final int mlvlMod;
    private final String namePoolKey;

    DifficultyTier(String tintHex, String tintEffectId, int mlvlMod, String namePoolKey) {
        this.tintHex = tintHex;
        this.tintEffectId = tintEffectId;
        this.mlvlMod = mlvlMod;
        this.namePoolKey = namePoolKey;
    }

    public String tintHex()      { return tintHex; }
    public String tintEffectId() { return tintEffectId; }
    public int mlvlMod()         { return mlvlMod; }
    public String namePoolKey()  { return namePoolKey; }

    @Nullable
    public static DifficultyTier fromName(@Nullable String name) {
        if (name == null) return null;
        for (DifficultyTier t : values()) if (t.name().equalsIgnoreCase(name)) return t;
        return null;
    }
}
