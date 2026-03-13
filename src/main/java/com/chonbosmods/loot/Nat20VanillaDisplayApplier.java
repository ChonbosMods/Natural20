package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20GemDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20GemRegistry;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Applies vanilla display properties to an ItemStack after loot generation.
 *
 * Sets the generated name and a condensed plain-text description summarizing
 * affixes, sockets, and requirements on the Nat20LootData metadata.
 *
 * Note on SDK limitations: the Hytale SDK does not provide per-stack setName(),
 * setDescription(), or setQuality() APIs. ItemQuality, name, and description are
 * properties of the Item definition (asset-level), not the ItemStack. This applier
 * stores the display text in the Nat20LootData metadata so that our custom UI panel
 * can read and render it. The ItemQuality JSON assets (nat20_common through
 * nat20_legendary) remain useful for items defined with those quality IDs in their
 * Item JSON.
 */
public class Nat20VanillaDisplayApplier {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Nat20RarityRegistry rarityRegistry;
    private final Nat20AffixRegistry affixRegistry;
    private final Nat20GemRegistry gemRegistry;

    public Nat20VanillaDisplayApplier(Nat20RarityRegistry rarityRegistry,
                                       Nat20AffixRegistry affixRegistry,
                                       Nat20GemRegistry gemRegistry) {
        this.rarityRegistry = rarityRegistry;
        this.affixRegistry = affixRegistry;
        this.gemRegistry = gemRegistry;
    }

    /**
     * Build the display description for a Nat20LootData and store it in the loot data,
     * then return a new ItemStack with the updated metadata.
     *
     * @param stack    the base ItemStack (with Nat20LootData already in metadata)
     * @param lootData the loot data to apply display properties from
     * @return a new ItemStack with the updated Nat20LootData containing the description
     */
    public ItemStack apply(ItemStack stack, Nat20LootData lootData) {
        String description = buildDescription(lootData);
        lootData.setDescription(description);

        return stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);
    }

    /**
     * Build a condensed plain-text description from the loot data.
     *
     * Format per affix line: {sign}{value} {targetStat} ({scalingStat})
     * Uses base values (no player context at creation time).
     */
    public String buildDescription(Nat20LootData lootData) {
        StringBuilder sb = new StringBuilder();

        // Affix lines
        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            Nat20AffixDef affixDef = affixRegistry.get(rolledAffix.id());
            if (affixDef == null) continue;

            AffixValueRange range = affixDef.getValuesForRarity(lootData.getRarity());
            if (range == null) continue;

            double baseValue = range.interpolate(lootData.getLootLevel());

            String sign = baseValue >= 0 ? "+" : "";
            String valueStr;
            if ("MULTIPLICATIVE".equals(affixDef.modifierType())) {
                valueStr = sign + String.format("%.1f%%", baseValue * 100);
            } else {
                valueStr = sign + String.format("%.1f", baseValue);
            }

            String scalingLabel = affixDef.statScaling() != null
                    ? " (" + affixDef.statScaling().primary().name() + ")"
                    : "";

            if (!sb.isEmpty()) sb.append("\n");
            sb.append(valueStr).append(" ").append(affixDef.targetStat()).append(scalingLabel);
        }

        // Socket lines
        if (lootData.getSockets() > 0) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("Sockets:");
            List<SocketedGem> gems = lootData.getGems();
            for (int i = 0; i < lootData.getSockets(); i++) {
                if (i < gems.size()) {
                    SocketedGem gem = gems.get(i);
                    Nat20GemDef gemDef = gemRegistry.get(gem.id());
                    String gemName = gemDef != null ? extractDisplayWord(gemDef.displayName()) : gem.id();
                    String purity = gem.purity().key();
                    sb.append(" [").append(purity).append(" ").append(gemName).append("]");
                } else {
                    sb.append(" [Empty]");
                }
            }
        }

        // Requirement line
        Nat20RarityDef rarity = rarityRegistry.get(lootData.getRarity());
        if (rarity != null && rarity.statRequirement() > 0) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("Requires: Any ").append(rarity.statRequirement()).append("+");
        }

        return sb.toString();
    }

    /**
     * Get the ItemQuality asset ID for a given rarity.
     * Returns the quality ID string (e.g., "nat20_common") that corresponds to the
     * rarity. These IDs match the JSON files in Server/Item/Qualities/.
     *
     * Note: the Hytale SDK does not support per-stack quality overrides. Quality is
     * set on the Item definition (asset-level) via qualityId. This method is provided
     * for reference and potential future use if per-stack quality becomes available.
     *
     * @param rarityId the rarity ID (e.g., "Common", "Legendary")
     * @return the ItemQuality asset ID, or null if the rarity is not found
     */
    @Nullable
    public String getQualityIdForRarity(String rarityId) {
        Nat20RarityDef rarity = rarityRegistry.get(rarityId);
        if (rarity == null) return null;
        return "nat20_" + rarityId.toLowerCase();
    }

    private String extractDisplayWord(String localizationKey) {
        int lastDot = localizationKey.lastIndexOf('.');
        String word = (lastDot >= 0) ? localizationKey.substring(lastDot + 1) : localizationKey;
        if (!word.isEmpty()) {
            word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return word;
    }
}
