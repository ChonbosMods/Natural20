package com.chonbosmods.loot.mob;

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
 * Rolls mob-eligible gear affixes for a spawning mob with a slot budget.
 * Picks entries until the budget (tier-driven from mob_scaling.json) is spent.
 * Slot-2 affixes (thorns, life_leech, evasion) consume two of the available slots.
 */
public final class Nat20MobAffixRoller {

    private final Nat20AffixRegistry affixRegistry;

    public Nat20MobAffixRoller(Nat20AffixRegistry affixRegistry) {
        this.affixRegistry = affixRegistry;
    }

    /**
     * Roll affixes for a mob.
     *
     * @param slotBudget total affix slots available (from MobScalingConfig.affixCountFor).
     * @param difficulty rarity band to pick values from.
     * @param rng        shared RNG.
     * @return rolled affix entries, empty if no eligible affixes in the pool.
     */
    public List<RolledAffix> roll(int slotBudget, DifficultyTier difficulty, Random rng) {
        if (slotBudget <= 0) return List.of();

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

        List<RolledAffix> rolled = new ArrayList<>();
        int budget = slotBudget;
        for (Nat20AffixDef def : pool) {
            int cost = Math.max(1, def.affixSlotCost());
            if (cost > budget) continue;
            AffixValueRange range = def.getValuesForRarity(rarityKey);
            rolled.add(new RolledAffix(def.id(), range.min(), range.max()));
            budget -= cost;
            if (budget == 0) break;
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
