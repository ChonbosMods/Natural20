package com.chonbosmods.loot.mob;

import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.Tier;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rolls Nat20 drop counts per mob death based on {@link Tier} and
 * {@link DifficultyTier}. See
 * {@code docs/plans/2026-04-17-mob-loot-pool-redesign-design.md} for table.
 *
 * <p>REGULAR → 0.
 * <p>CHAMPION × {UNCOMMON, RARE, EPIC} → single Bernoulli trial at 18%.
 * LEGENDARY CHAMPION is an invalid combo (0 drops + one-shot warning).
 * <p>BOSS/DUNGEON_BOSS → guaranteed base + N independent 30% bonus rolls.
 */
public final class Nat20MobDropCount {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobDropCount");

    private static final float CHAMPION_CHANCE_UNCOMMON = 0.18f;
    private static final float CHAMPION_CHANCE_RARE     = 0.18f;
    private static final float CHAMPION_CHANCE_EPIC     = 0.18f;
    private static final float BOSS_BONUS_CHANCE        = 0.30f;

    private static final AtomicBoolean LEGENDARY_CHAMPION_WARNED = new AtomicBoolean(false);

    private Nat20MobDropCount() {}

    public static int roll(Tier tier, @Nullable DifficultyTier difficulty, Random rng) {
        if (tier == null || difficulty == null) return 0;
        return switch (tier) {
            case REGULAR -> 0;
            case CHAMPION -> rollChampion(difficulty, rng);
            case BOSS, DUNGEON_BOSS -> rollBoss(difficulty, rng);
        };
    }

    private static int rollChampion(DifficultyTier difficulty, Random rng) {
        float chance = switch (difficulty) {
            case UNCOMMON -> CHAMPION_CHANCE_UNCOMMON;
            case RARE     -> CHAMPION_CHANCE_RARE;
            case EPIC     -> CHAMPION_CHANCE_EPIC;
            case LEGENDARY -> {
                if (LEGENDARY_CHAMPION_WARNED.compareAndSet(false, true)) {
                    LOGGER.atWarning().log("LEGENDARY CHAMPION combo rolled; champions should top out at EPIC. Returning 0 drops.");
                }
                yield 0f;
            }
        };
        return rng.nextFloat() < chance ? 1 : 0;
    }

    private static int rollBoss(DifficultyTier difficulty, Random rng) {
        int guaranteed;
        int bonusRolls;
        switch (difficulty) {
            case UNCOMMON  -> { guaranteed = 1; bonusRolls = 1; }
            case RARE      -> { guaranteed = 1; bonusRolls = 2; }
            case EPIC      -> { guaranteed = 1; bonusRolls = 3; }
            case LEGENDARY -> { guaranteed = 2; bonusRolls = 3; }
            default        -> { guaranteed = 1; bonusRolls = 0; }
        }
        int extras = 0;
        for (int i = 0; i < bonusRolls; i++) {
            if (rng.nextFloat() < BOSS_BONUS_CHANCE) extras++;
        }
        return guaranteed + extras;
    }
}
