package com.chonbosmods.loot;

import com.chonbosmods.loot.AffixType;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.GemBonus;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20GemDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20GemRegistry;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

public class Nat20ModifierManager {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_KEY_PREFIX = "nat20:affix:";
    private static final String GEM_KEY_PREFIX = "nat20:gem:";

    private final Nat20RarityRegistry rarityRegistry;
    private final Nat20AffixRegistry affixRegistry;
    private final Nat20GemRegistry gemRegistry;

    public Nat20ModifierManager(Nat20RarityRegistry rarityRegistry,
                                 Nat20AffixRegistry affixRegistry,
                                 Nat20GemRegistry gemRegistry) {
        this.rarityRegistry = rarityRegistry;
        this.affixRegistry = affixRegistry;
        this.gemRegistry = gemRegistry;
    }

    /**
     * Apply all stat modifiers from a loot item's affixes and socketed gems to the entity's stat map.
     *
     * @param statMap      the entity's stat map
     * @param stack        the item being equipped
     * @param slotName     equipment slot identifier (used to namespace modifier keys)
     * @param categoryKey  equipment category key for gem bonus lookup
     * @param playerStats  player's D&D stats for scaling calculations, or null to skip scaling
     */
    public void applyModifiers(EntityStatMap statMap, ItemStack stack,
                                String slotName, String categoryKey,
                                @Nullable PlayerStats playerStats) {
        Nat20LootData lootData = stack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        Nat20RarityDef rarity = rarityRegistry.get(lootData.getRarity());
        if (rarity == null) {
            LOGGER.atWarning().log("Unknown rarity '%s' on equipped item", lootData.getRarity());
            return;
        }

        applyAffixModifiers(statMap, lootData, slotName, playerStats);
        applyGemModifiers(statMap, lootData, categoryKey, playerStats);
    }

    /**
     * Remove all stat modifiers that were applied from a loot item's affixes and gems.
     *
     * @param statMap  the entity's stat map
     * @param slotName equipment slot identifier (must match the slotName used during apply)
     * @param lootData the loot data from the item being unequipped, or null (no-op)
     */
    public void removeModifiers(EntityStatMap statMap, String slotName, @Nullable Nat20LootData lootData) {
        if (lootData == null) return;

        for (var affix : lootData.getAffixes()) {
            Nat20AffixDef affixDef = affixRegistry.get(affix.id());
            if (affixDef == null || affixDef.targetStat() == null) continue;

            int statIndex = resolveStatIndex(affixDef.targetStat());
            if (statIndex == AssetMapWithIndexes.NOT_FOUND) continue;

            String key = AFFIX_KEY_PREFIX + affix.id() + ":" + slotName;
            statMap.removeModifier(statIndex, key);
        }

        List<SocketedGem> gems = lootData.getGems();
        for (int i = 0; i < gems.size(); i++) {
            Nat20GemDef gemDef = gemRegistry.get(gems.get(i).id());
            if (gemDef == null) continue;

            for (var bonus : gemDef.bonusesBySlot().values()) {
                int statIndex = resolveStatIndex(bonus.stat());
                if (statIndex == AssetMapWithIndexes.NOT_FOUND) continue;

                String key = GEM_KEY_PREFIX + gems.get(i).id() + ":" + i;
                statMap.removeModifier(statIndex, key);
            }
        }
    }

    private void applyAffixModifiers(EntityStatMap statMap, Nat20LootData lootData,
                                      String slotName, @Nullable PlayerStats playerStats) {
        for (var rolledAffix : lootData.getAffixes()) {
            Nat20AffixDef affixDef = affixRegistry.get(rolledAffix.id());
            if (affixDef == null || affixDef.targetStat() == null) continue;

            // EFFECT/ABILITY affixes are handled by their own systems, not StaticModifiers
            if (affixDef.type() == AffixType.EFFECT || affixDef.type() == AffixType.ABILITY) continue;

            // Check stat requirements: skip modifier if player doesn't meet minimums
            if (affixDef.statRequirement() != null && playerStats != null) {
                boolean requirementsMet = true;
                for (var req : affixDef.statRequirement().entrySet()) {
                    if (playerStats.stats()[req.getKey().index()] < req.getValue()) {
                        requirementsMet = false;
                        break;
                    }
                }
                if (!requirementsMet) {
                    LOGGER.atFine().log("Affix '%s' stat requirement not met, skipping modifier",
                            rolledAffix.id());
                    continue;
                }
            }

            AffixValueRange range = affixDef.getValuesForRarity(lootData.getRarity());
            if (range == null) continue;

            // Interpolate base value from loot level
            double baseValue = range.interpolate(lootData.getLootLevel());

            // Apply stat scaling if player stats are available
            double effectiveValue = baseValue;
            if (playerStats != null && affixDef.statScaling() != null) {
                Stat primary = affixDef.statScaling().primary();
                int modifier = playerStats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * affixDef.statScaling().factor());
            }

            int statIndex = resolveStatIndex(affixDef.targetStat());
            if (statIndex == AssetMapWithIndexes.NOT_FOUND) {
                LOGGER.atWarning().log("Unknown target stat '%s' for affix '%s'",
                        affixDef.targetStat(), rolledAffix.id());
                continue;
            }

            String key = AFFIX_KEY_PREFIX + rolledAffix.id() + ":" + slotName;
            StaticModifier mod = createModifier(affixDef.modifierType(), (float) effectiveValue);
            statMap.putModifier(statIndex, key, mod);
        }
    }

    private void applyGemModifiers(EntityStatMap statMap, Nat20LootData lootData,
                                    String categoryKey, @Nullable PlayerStats playerStats) {
        List<SocketedGem> gems = lootData.getGems();
        for (int i = 0; i < gems.size(); i++) {
            SocketedGem gem = gems.get(i);
            Nat20GemDef gemDef = gemRegistry.get(gem.id());
            if (gemDef == null) continue;

            GemBonus bonus = gemDef.getBonusForCategory(categoryKey);
            if (bonus == null) continue;

            double purityMult = gemDef.getPurityMultiplier(gem.purity().key());
            double baseValue = bonus.baseValue() * purityMult;

            // Apply stat affinity scaling if player stats are available
            double effectiveValue = baseValue;
            if (playerStats != null && gemDef.statAffinity() != null) {
                int mod = playerStats.getModifier(gemDef.statAffinity());
                effectiveValue = baseValue * (1.0 + mod * gemDef.affinityScalingFactor());
            }

            int statIndex = resolveStatIndex(bonus.stat());
            if (statIndex == AssetMapWithIndexes.NOT_FOUND) continue;

            String key = GEM_KEY_PREFIX + gem.id() + ":" + i;
            StaticModifier modifier = createModifier(bonus.type(), (float) effectiveValue);
            statMap.putModifier(statIndex, key, modifier);
        }
    }

    /**
     * Resolve a stat name (e.g., "AttackDamage", "Health") to its EntityStatType index.
     */
    private int resolveStatIndex(String statName) {
        try {
            var assetMap = EntityStatType.getAssetMap();
            return assetMap.getIndex(statName);
        } catch (Exception e) {
            return AssetMapWithIndexes.NOT_FOUND;
        }
    }

    private StaticModifier createModifier(String modifierType, float amount) {
        var calcType = "MULTIPLICATIVE".equals(modifierType)
            ? StaticModifier.CalculationType.MULTIPLICATIVE
            : StaticModifier.CalculationType.ADDITIVE;
        return new StaticModifier(Modifier.ModifierTarget.MAX, calcType, amount);
    }
}
