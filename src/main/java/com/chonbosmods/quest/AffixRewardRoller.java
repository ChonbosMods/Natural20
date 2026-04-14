package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Rolls a quest-reward item at a given rarity tier and item level by delegating
 * to the Nat20 loot/affix system. Has no fallback path: if the underlying system
 * cannot produce a valid stack, this method throws and quest generation fails
 * loudly. No placeholder, no silent zero-value data.
 *
 * <p>Composition path (matches {@code LootCommand}):
 * <ol>
 *   <li>Pick a base item uniformly at random from {@link Nat20LootEntryRegistry}.</li>
 *   <li>Resolve its category + display name from the same registry.</li>
 *   <li>Resolve {@code tier} ({@code "Common"}..{@code "Legendary"}) to a {@code qualityValue}
 *       via {@code Nat20RarityRegistry}.</li>
 *   <li>Call {@code Nat20LootPipeline.generate(itemId, baseName, categoryKey, tier, tier,
 *       random, ilvl)} to produce a {@link Nat20LootData} with a registered uniqueItemId.</li>
 *   <li>Wrap in an {@link ItemStack} with the loot data attached as metadata, exactly the
 *       same pattern {@code LootCommand} uses.</li>
 * </ol>
 */
public final class AffixRewardRoller {

    private static final Set<String> VALID_TIERS =
            Set.of("Common", "Uncommon", "Rare", "Epic", "Legendary");

    private AffixRewardRoller() {}

    /**
     * @param tier   vanilla Hytale quality id: "Common" | "Uncommon" | "Rare" | "Epic" | "Legendary"
     * @param ilvl   item level (must be > 0)
     * @param random RNG source for the roll
     * @return the rolled reward stack with {@link Nat20LootData} attached as metadata
     * @throws IllegalArgumentException if {@code tier} is not in the whitelist or {@code ilvl <= 0}
     * @throws IllegalStateException    if the loot system is not initialized or cannot produce
     *                                  a valid stack at the requested tier/ilvl
     */
    public static ItemStack roll(String tier, int ilvl, Random random) {
        if (tier == null || !VALID_TIERS.contains(tier)) {
            throw new IllegalArgumentException(
                    "Invalid reward tier '" + tier + "'; expected one of " + VALID_TIERS);
        }
        if (ilvl <= 0) {
            throw new IllegalArgumentException("Reward ilvl must be > 0 (got " + ilvl + ")");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }

        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();
        if (lootSystem == null) {
            throw new IllegalStateException(
                    "Nat20LootSystem unavailable; cannot roll reward (tier=" + tier
                            + ", ilvl=" + ilvl + ")");
        }

        Nat20RarityDef rarity = lootSystem.getRarityRegistry().get(tier);
        if (rarity == null) {
            throw new IllegalStateException(
                    "Rarity '" + tier + "' not loaded by Nat20RarityRegistry; "
                            + "cannot roll reward (ilvl=" + ilvl + ")");
        }
        int qualityValue = rarity.qualityValue();

        Nat20LootEntryRegistry entryRegistry = lootSystem.getLootEntryRegistry();
        List<String> itemIds = new ArrayList<>(entryRegistry.getAllItemIds());
        if (itemIds.isEmpty()) {
            throw new IllegalStateException(
                    "Nat20LootEntryRegistry has no entries; cannot roll reward (tier="
                            + tier + ", ilvl=" + ilvl + ")");
        }
        // Item ids in the registry match the keys used by LootCommand (which passes
        // item.getId() straight into the same registry lookups), so use them as-is.
        String itemId = itemIds.get(random.nextInt(itemIds.size()));

        String categoryKey = entryRegistry.getManualCategoryKey(itemId);
        if (categoryKey == null) {
            throw new IllegalStateException(
                    "No category mapping for base item '" + itemId
                            + "'; cannot roll reward (tier=" + tier + ", ilvl=" + ilvl + ")");
        }

        String baseName = entryRegistry.getDisplayName(itemId);
        if (baseName == null) {
            baseName = lootSystem.getItemRegistry().resolveItemDisplayName(itemId);
        }
        if (baseName == null) {
            throw new IllegalStateException(
                    "No display name resolvable for base item '" + itemId
                            + "'; cannot roll reward (tier=" + tier + ", ilvl=" + ilvl + ")");
        }

        Nat20LootData lootData = lootSystem.getPipeline().generate(
                itemId, baseName, categoryKey,
                qualityValue, qualityValue,
                random, ilvl);
        if (lootData == null) {
            throw new IllegalStateException(
                    "Nat20LootPipeline.generate returned null for itemId='" + itemId
                            + "', tier=" + tier + ", ilvl=" + ilvl);
        }

        String stackItemId = lootData.getUniqueItemId() != null
                ? lootData.getUniqueItemId()
                : itemId;
        return new ItemStack(stackItemId, 1)
                .withMetadata(Nat20LootData.METADATA_KEY, lootData);
    }
}
