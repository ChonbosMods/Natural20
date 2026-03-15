package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.GemBonus;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20GemDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.display.AffixLine;
import com.chonbosmods.loot.display.RequirementLine;
import com.chonbosmods.loot.display.SocketLine;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20GemRegistry;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Nat20ItemRenderer {

    private final Nat20RarityRegistry rarityRegistry;
    private final Nat20AffixRegistry affixRegistry;
    private final Nat20GemRegistry gemRegistry;

    public Nat20ItemRenderer(Nat20RarityRegistry rarityRegistry,
                              Nat20AffixRegistry affixRegistry,
                              Nat20GemRegistry gemRegistry) {
        this.rarityRegistry = rarityRegistry;
        this.affixRegistry = affixRegistry;
        this.gemRegistry = gemRegistry;
    }

    @Nullable
    public Nat20ItemDisplayData resolve(ItemStack stack, @Nullable PlayerStats playerStats) {
        Nat20LootData lootData = stack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return null;
        return resolve(lootData, playerStats);
    }

    @Nullable
    public Nat20ItemDisplayData resolve(Nat20LootData lootData, @Nullable PlayerStats playerStats) {
        Nat20RarityDef rarity = rarityRegistry.get(lootData.getRarity());
        if (rarity == null) return null;

        // Affix lines
        List<AffixLine> affixes = new ArrayList<>();
        for (var rolledAffix : lootData.getAffixes()) {
            Nat20AffixDef affixDef = affixRegistry.get(rolledAffix.id());
            if (affixDef == null) continue;

            AffixValueRange range = affixDef.getValuesForRarity(lootData.getRarity());
            if (range == null) continue;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;
            if (playerStats != null && affixDef.statScaling() != null) {
                int mod = playerStats.getModifier(affixDef.statScaling().primary());
                effectiveValue = baseValue * (1.0 + mod * affixDef.statScaling().factor());
            }

            // Format value and unit separately
            String value;
            String unit;
            String sign = effectiveValue >= 0 ? "+" : "";
            if ("MULTIPLICATIVE".equals(affixDef.modifierType())) {
                value = sign + String.format("%.0f", effectiveValue * 100);
                unit = "%";
            } else {
                value = sign + String.format("%.1f", effectiveValue);
                unit = "";
            }

            String affixName = extractDisplayWord(affixDef.displayName());
            String scalingStat = affixDef.statScaling() != null
                    ? affixDef.statScaling().primary().name()
                    : null;
            String type = affixDef.type().name();

            // Per-affix requirement check
            boolean requirementMet = true;
            String requirementText = null;
            if (affixDef.statRequirement() != null && !affixDef.statRequirement().isEmpty()) {
                requirementText = formatStatRequirement(affixDef.statRequirement());
                if (playerStats != null) {
                    for (Map.Entry<Stat, Integer> req : affixDef.statRequirement().entrySet()) {
                        if (playerStats.stats()[req.getKey().index()] < req.getValue()) {
                            requirementMet = false;
                            break;
                        }
                    }
                }
            }

            affixes.add(new AffixLine(
                    affixName, value, unit, affixDef.targetStat(),
                    scalingStat, type, requirementMet, requirementText,
                    affixDef.description(), affixDef.cooldown(), affixDef.procChance()
            ));
        }

        // Socket lines
        List<SocketLine> sockets = new ArrayList<>();
        List<SocketedGem> gems = lootData.getGems();
        for (int i = 0; i < lootData.getSockets(); i++) {
            if (i < gems.size()) {
                SocketedGem gem = gems.get(i);
                Nat20GemDef gemDef = gemRegistry.get(gem.id());
                String gemName = gemDef != null ? extractDisplayWord(gemDef.displayName()) : gem.id();
                String purity = gem.purity().key();
                String gemColor = (gemDef != null && gemDef.statAffinity() != null)
                        ? gemDef.statAffinity().color()
                        : "#ffcc00";

                // Compute bonus display values if gem def is available
                String bonusValue = null;
                String bonusStat = null;
                if (gemDef != null) {
                    // Find a matching bonus for this item (use first available category)
                    for (var category : gemDef.bonusesBySlot().keySet()) {
                        GemBonus bonus = gemDef.getBonusForCategory(category);
                        if (bonus != null) {
                            double raw = bonus.baseValue() * gemDef.getPurityMultiplier(purity);
                            bonusValue = "+" + String.format("%.1f", raw);
                            bonusStat = bonus.stat();
                            break;
                        }
                    }
                }

                sockets.add(new SocketLine(i, true, gemName, purity, gemColor, bonusValue, bonusStat));
            } else {
                sockets.add(new SocketLine(i, false, null, null, null, null, null));
            }
        }

        // Rarity requirement: "Any X+" rule: met if ANY of the player's six stats meets the threshold
        RequirementLine requirement = null;
        if (rarity.statRequirement() > 0) {
            boolean met = true;
            if (playerStats != null) {
                met = false;
                for (Stat stat : Stat.values()) {
                    if (playerStats.stats()[stat.index()] >= rarity.statRequirement()) {
                        met = true;
                        break;
                    }
                }
            }
            String text = "Any " + rarity.statRequirement() + "+";
            requirement = new RequirementLine(text, met);
        }

        return new Nat20ItemDisplayData(
                lootData.getGeneratedName(),
                lootData.getRarity(),
                rarity.color(),
                rarity.tooltipTexture(),
                rarity.slotTexture(),
                affixes,
                sockets,
                requirement,
                lootData.getDescription()
        );
    }

    private String formatStatRequirement(Map<Stat, Integer> statRequirement) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Stat, Integer> entry : statRequirement.entrySet()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(entry.getKey().name()).append(" ").append(entry.getValue());
        }
        return sb.toString();
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
