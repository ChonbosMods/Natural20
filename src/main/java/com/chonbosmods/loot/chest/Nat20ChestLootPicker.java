package com.chonbosmods.loot.chest;

import com.chonbosmods.loot.CategoryWeightedPicker;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootPipeline;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.mob.Nat20ItemTierResolver;
import com.chonbosmods.loot.mob.Nat20MobLootPool;
import com.chonbosmods.loot.registry.Nat20ItemRegistry;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Chest-side loot picker bridging the gear-item pool to {@link Nat20LootPipeline}.
 *
 * <p>Mirrors {@link com.chonbosmods.loot.mob.Nat20MobLootListener}'s per-drop logic
 * (sample itemId : infer category : resolve display name : pipeline.generate) but
 * parameterised on an explicit {@code ilvl} instead of reading a mob component.
 *
 * <p>Chests have no role context, so no native bias applies (the mob-side value
 * is {@link Nat20MobLootPool#NATIVE_LIST_BIAS} = 5%). Selection delegates to
 * {@link CategoryWeightedPicker} for category-weighted random pick over the
 * bucketed global pool built from {@link Nat20MobLootPool#buildGlobalBuckets}.
 */
public final class Nat20ChestLootPicker {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestLootPicker");

    /** Bounded retry budget when a sampled item resolves to a null category. */
    private static final int MAX_PICK_ATTEMPTS = 8;

    private final Nat20LootSystem lootSystem;

    public Nat20ChestLootPicker(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    /**
     * Roll one piece of chest loot using the default ilvl rarity gate.
     */
    public Optional<Nat20LootData> pickLoot(int ilvl, Random rng) {
        int[] gate = Nat20LootPipeline.rarityGateForIlvl(ilvl);
        return pickLoot(ilvl, gate[0], gate[1], rng);
    }

    /**
     * Roll one piece of chest loot with caller-supplied rarity-tier clamps. Bounds
     * are intersected with the ilvl gate so callers can tighten the ceiling (e.g.
     * bias a bonus roll toward Common/Uncommon) without bypassing ilvl policy.
     *
     * @param ilvl        area level (same semantics as mob {@code getAreaLevel()})
     * @param minTier     minimum rarity tier (1=Common ... 5=Legendary)
     * @param maxTier     maximum rarity tier
     * @param rng         random source
     * @return generated loot data, or empty if pool empty, clamp impossible, or pipeline fails
     */
    public Optional<Nat20LootData> pickLoot(int ilvl, int minTier, int maxTier, Random rng) {
        int[] gate = Nat20LootPipeline.rarityGateForIlvl(ilvl);
        int effectiveMin = Math.max(minTier, gate[0]);
        int effectiveMax = Math.min(maxTier, gate[1]);
        if (effectiveMin > effectiveMax) {
            LOGGER.atWarning().log(
                    "Chest pick skipped: rarity clamp [%d,%d] incompatible with ilvl=%d gate [%d,%d]",
                    minTier, maxTier, ilvl, gate[0], gate[1]);
            return Optional.empty();
        }

        Map<String, List<String>> buckets = Nat20MobLootPool.buildGlobalBuckets(
                lootSystem.getLootEntryRegistry(), ilvl);
        boolean allEmpty = buckets.values().stream().allMatch(List::isEmpty);
        if (allEmpty) {
            LOGGER.atWarning().log("Empty chest loot pool at ilvl=%d; nothing to generate", ilvl);
            return Optional.empty();
        }

        Nat20LootPipeline pipeline = lootSystem.getPipeline();

        for (int attempt = 0; attempt < MAX_PICK_ATTEMPTS; attempt++) {
            String itemId = CategoryWeightedPicker.pick(buckets, rng);
            if (itemId == null) break;
            String categoryKey = Nat20ItemTierResolver.inferCategory(itemId);
            if (categoryKey == null) continue;
            String baseName = resolveDisplayName(itemId);

            Nat20LootData data = pipeline.generate(
                    itemId, baseName, categoryKey,
                    effectiveMin, effectiveMax,
                    rng, ilvl);
            if (data == null) {
                LOGGER.atWarning().log("Pipeline returned null for chest pick (itemId=%s ilvl=%d); re-rolling",
                        itemId, ilvl);
                continue;
            }
            LOGGER.atInfo().log("Chest pick: %s [%s] from %s (ilvl=%d tier=[%d,%d])",
                    data.getGeneratedName(), data.getRarity(), itemId, ilvl, effectiveMin, effectiveMax);
            return Optional.of(data);
        }

        LOGGER.atWarning().log("Chest pick exhausted %d attempts at ilvl=%d without a valid roll",
                MAX_PICK_ATTEMPTS, ilvl);
        return Optional.empty();
    }

    private String resolveDisplayName(String itemId) {
        Nat20LootEntryRegistry entryRegistry = lootSystem.getLootEntryRegistry();
        String name = entryRegistry.getDisplayName(itemId);
        if (name != null) return name;
        Nat20ItemRegistry itemRegistry = lootSystem.getItemRegistry();
        if (itemRegistry != null) {
            name = itemRegistry.resolveItemDisplayName(itemId);
            if (name != null) return name;
        }
        return itemId;
    }
}
