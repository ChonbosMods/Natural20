package com.chonbosmods.loot.mob;

import com.chonbosmods.loot.Nat20DotAffix;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.progression.DifficultyTier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Rolls mob-eligible gear affixes for a spawning mob. Picks N distinct affixes
 * from the shuffled pool, where N is the role × difficulty count from mob_scaling.json.
 */
public final class Nat20MobAffixRoller {

    private final Nat20AffixRegistry affixRegistry;

    public Nat20MobAffixRoller(Nat20AffixRegistry affixRegistry) {
        this.affixRegistry = affixRegistry;
    }

    /**
     * Roll affixes for a mob.
     *
     * @param affixCount how many distinct affixes to pick (from MobScalingConfig.affixCountFor).
     * @param difficulty rarity band to pick values from.
     * @param rng        shared RNG.
     * @return rolled affix entries, empty if no eligible affixes in the pool.
     */
    public List<RolledAffix> roll(int affixCount, DifficultyTier difficulty, Random rng) {
        if (affixCount <= 0) return List.of();

        String rarityKey = rarityKeyFor(difficulty);
        List<Nat20AffixDef> pool = new ArrayList<>();
        for (Nat20AffixDef def : affixRegistry.getAll()) {
            if (!def.mobEligible()) continue;
            AffixValueRange range = def.getValuesForRarity(rarityKey);
            if (range == null) continue;
            pool.add(def);
        }
        if (pool.isEmpty()) return List.of();

        Collections.shuffle(pool, rng);

        int take = Math.min(affixCount, pool.size());
        List<RolledAffix> rolled = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            Nat20AffixDef def = pool.get(i);
            AffixValueRange range = def.getValuesForRarity(rarityKey);
            double duration = Nat20DotAffix.isDotAffix(def.id())
                    ? Nat20DotAffix.rollDuration(rng) : 0.0;
            rolled.add(new RolledAffix(def.id(), range.min(), range.max(), duration));
        }
        return rolled;
    }

    /** Maps a DifficultyTier to the rarity key used in ValuesPerRarity JSON. */
    private static String rarityKeyFor(DifficultyTier difficulty) {
        return switch (difficulty) {
            case UNCOMMON -> "Uncommon";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Legendary";
        };
    }
}
