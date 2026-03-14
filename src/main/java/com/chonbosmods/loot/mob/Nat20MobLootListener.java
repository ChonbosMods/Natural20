package com.chonbosmods.loot.mob;

import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootPipeline;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates enhanced loot drops when an affixed mob dies.
 *
 * <p>Loot enhancement rules:
 * <ul>
 *   <li>Each mob affix shifts the rarity floor up by one tier</li>
 *   <li>The product of all affix {@code lootBonusMultiplier} values determines drop count</li>
 *   <li>Champion and Boss tiers guarantee at least one affix-eligible item drop</li>
 * </ul>
 *
 * <p>This class exposes {@link #onMobDeath} as a public method that can be called
 * from whichever hook we wire into mob death (damage event, DeathSystems callback,
 * or affix manager callback).
 *
 * TODO: register for the actual mob death event once a suitable SDK hook is confirmed
 */
public class Nat20MobLootListener {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /** Rarity quality values: Common=101, Uncommon=102, Rare=103, Epic=104, Legendary=105. */
    private static final int QUALITY_COMMON = 101;
    private static final int QUALITY_LEGENDARY = 105;

    private final Nat20LootSystem lootSystem;

    public Nat20MobLootListener(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    /**
     * Process loot generation for an affixed mob that has just died.
     *
     * <p>Looks up the mob's applied affixes, computes a rarity floor and drop count,
     * generates loot items through the pipeline, and cleans up affix tracking data.
     *
     * @param mobRef the entity reference for the dead mob
     * @param store  the entity store containing the mob
     * @return the list of generated loot data, or an empty list if the mob had no affixes
     */
    public List<Nat20LootData> onMobDeath(Ref<EntityStore> mobRef, Store<EntityStore> store) {
        Nat20MobAffixManager manager = lootSystem.getMobAffixManager();
        List<Nat20MobAffixDef> affixes = manager.getAppliedAffixes(mobRef);
        if (affixes == null || affixes.isEmpty()) {
            return List.of();
        }

        LOGGER.atInfo().log("Processing loot for affixed mob %s with %d affix(es): %s",
                mobRef, affixes.size(),
                affixes.stream().map(Nat20MobAffixDef::displayName).toList());

        // 1. Calculate rarity floor: each affix shifts floor up by 1 tier
        int rarityFloor = Math.min(QUALITY_COMMON + affixes.size(), QUALITY_LEGENDARY);

        // 2. Calculate loot bonus multiplier: product of all affix multipliers
        double lootMultiplier = 1.0;
        for (Nat20MobAffixDef affix : affixes) {
            lootMultiplier *= affix.lootBonusMultiplier();
        }

        // 3. Determine drop count from multiplier (minimum 1)
        int dropCount = Math.max(1, (int) Math.round(lootMultiplier));

        // 4. Champion/Boss guarantee: at least 1 drop (already enforced by max(1, ...) above,
        //    but this makes the intent explicit and allows future escalation)
        EncounterTier tier = resolveTierFromAffixCount(affixes.size());
        if (tier == EncounterTier.CHAMPION || tier == EncounterTier.BOSS) {
            dropCount = Math.max(dropCount, 1);
        }

        LOGGER.atInfo().log("Loot params: rarityFloor=%d, lootMultiplier=%.2f, dropCount=%d, tier=%s",
                rarityFloor, lootMultiplier, dropCount, tier);

        // 5. Generate loot items via the pipeline
        List<Nat20LootData> results = generateDrops(dropCount, rarityFloor);

        // 6. Clean up tracked affixes to prevent memory leaks
        manager.clearMob(mobRef);

        return results;
    }

    /**
     * Generate the specified number of loot drops using the tier-clamped pipeline.
     *
     * <p>TODO: resolve base item ID and category from the mob's native drop table
     * instead of using placeholder values. Once we have the SDK drop table hook,
     * each drop should pick from the mob's eligible item pool.
     */
    private List<Nat20LootData> generateDrops(int dropCount, int rarityFloor) {
        Nat20LootPipeline pipeline = lootSystem.getPipeline();
        Nat20LootEntryRegistry entryRegistry = lootSystem.getLootEntryRegistry();
        Random random = ThreadLocalRandom.current();
        List<Nat20LootData> results = new ArrayList<>(dropCount);

        // TODO: replace placeholder item/category with mob drop table lookup
        String itemId = "Hytale:IronSword";
        String baseName = resolveDisplayName(entryRegistry, itemId, "Iron Sword");
        String categoryKey = resolveCategoryKey(entryRegistry, itemId, "melee_weapon");

        for (int i = 0; i < dropCount; i++) {
            Nat20LootData data = pipeline.generate(
                    itemId, baseName, categoryKey,
                    rarityFloor, QUALITY_LEGENDARY,
                    random);

            if (data != null) {
                results.add(data);
                LOGGER.atInfo().log("Generated mob drop %d/%d: %s [%s]",
                        i + 1, dropCount, data.getGeneratedName(), data.getRarity());
            } else {
                LOGGER.atWarning().log("Pipeline returned null for mob drop %d/%d", i + 1, dropCount);
            }
        }

        // TODO: spawn item entities at mob position or add to a pending drop list

        return results;
    }

    /**
     * Resolve the display name for an item, checking the entry registry first.
     */
    private String resolveDisplayName(Nat20LootEntryRegistry entryRegistry, String itemId,
                                       String fallback) {
        String name = entryRegistry.getDisplayName(itemId);
        return name != null ? name : fallback;
    }

    /**
     * Resolve the category key for an item, checking the entry registry first.
     */
    private String resolveCategoryKey(Nat20LootEntryRegistry entryRegistry, String itemId,
                                       String fallback) {
        String key = entryRegistry.getManualCategoryKey(itemId);
        return key != null ? key : fallback;
    }

    /**
     * Map affix count to an approximate encounter tier.
     * NORMAL=0, ENHANCED=1, ELITE=2, CHAMPION=3, BOSS=4.
     */
    private EncounterTier resolveTierFromAffixCount(int affixCount) {
        return EncounterTier.fromOrdinal(Math.min(affixCount, EncounterTier.values().length - 1));
    }
}
