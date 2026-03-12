package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20GemDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.registry.Nat20GemRegistry;
import com.chonbosmods.loot.registry.Nat20RarityRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.server.core.Message;
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

        Nat20RarityDef rarity = rarityRegistry.get(lootData.getRarity());
        if (rarity == null) return null;

        // Name: rarity-colored
        Message name = Message.raw(lootData.getGeneratedName()).color(rarity.color());

        // Rarity label
        Message rarityLabel = Message.raw(lootData.getRarity()).color(rarity.color());

        // Affix lines
        List<Message> affixLines = new ArrayList<>();
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

            String sign = effectiveValue >= 0 ? "+" : "";
            String valueStr;
            if ("MULTIPLICATIVE".equals(affixDef.modifierType())) {
                valueStr = sign + String.format("%.0f%%", effectiveValue * 100);
            } else {
                valueStr = sign + String.format("%.1f", effectiveValue);
            }

            String affixName = extractDisplayWord(affixDef.displayName());
            Message line = Message.raw(valueStr + " " + affixDef.targetStat() + " (" + affixName + ")")
                .color("#aaaaaa");
            affixLines.add(line);
        }

        // Socket lines
        List<Message> socketLines = new ArrayList<>();
        List<SocketedGem> gems = lootData.getGems();
        for (int i = 0; i < lootData.getSockets(); i++) {
            if (i < gems.size()) {
                SocketedGem gem = gems.get(i);
                Nat20GemDef gemDef = gemRegistry.get(gem.id());
                String gemName = gemDef != null ? extractDisplayWord(gemDef.displayName()) : gem.id();
                socketLines.add(Message.raw("[" + gem.purity().key() + " " + gemName + "]").color("#ffcc00"));
            } else {
                socketLines.add(Message.raw("[ Empty Socket ]").color("#666666"));
            }
        }

        // Requirement line
        Message requirementLine = null;
        if (rarity.statRequirement() > 0) {
            boolean met = playerStats == null || meetsRequirement(rarity, lootData, playerStats);
            String color = met ? "#33cc33" : "#cc3333";
            requirementLine = Message.raw("Requires: " + rarity.statRequirement() + " in primary stat")
                .color(color);
        }

        return new Nat20ItemDisplayData(
            name, rarityLabel, rarity.color(),
            affixLines, socketLines, requirementLine,
            rarity.slotTexture(), rarity.tooltipTexture()
        );
    }

    private boolean meetsRequirement(Nat20RarityDef rarity, Nat20LootData lootData, PlayerStats playerStats) {
        for (var rolledAffix : lootData.getAffixes()) {
            Nat20AffixDef affixDef = affixRegistry.get(rolledAffix.id());
            if (affixDef == null || affixDef.statRequirement() == null) continue;
            for (Map.Entry<Stat, Integer> req : affixDef.statRequirement().entrySet()) {
                if (playerStats.stats()[req.getKey().index()] < req.getValue()) {
                    return false;
                }
            }
        }
        return true;
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
