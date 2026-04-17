package com.chonbosmods.loot;

import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.GemBonus;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.Nat20GemDef;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.loot.display.AffixLine;
import com.chonbosmods.loot.display.Nat20AffixDisplay;
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

            // Interpolate at both ends of the rolled level range to get the per-item display range.
            double minValue = Nat20AffixScaling.interpolate(range, rolledAffix.minLevel(), lootData, rarityRegistry);
            double maxValue = Nat20AffixScaling.interpolate(range, rolledAffix.maxLevel(), lootData, rarityRegistry);
            double midValue = (minValue + maxValue) * 0.5;

            // Legacy numeric fields (still used by ComparisonDeltas for STAT-type diffing).
            String value;
            String unit;
            if ("MULTIPLICATIVE".equals(affixDef.modifierType())) {
                value = String.format("+%.0f", midValue * 100);
                unit = "%";
            } else {
                value = String.format("+%.1f", midValue);
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

            // Render the tooltip line with colour markup per the locked mapping.
            String renderedText = renderAffixLine(affixDef, rolledAffix, minValue, maxValue, midValue, rarity.color());

            affixes.add(new AffixLine(
                    affixName, value, unit, affixDef.targetStat(),
                    scalingStat, type, requirementMet, requirementText,
                    affixDef.description(), affixDef.cooldown(), affixDef.procChance(),
                    renderedText
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

    /**
     * Produce the fully colour-marked display line for a rolled affix per the locked
     * tooltip format. Returns a raw string with {@code <color is="#hex">...</color>} markup;
     * callers inject it into the tooltip/description pipeline as-is.
     */
    private String renderAffixLine(Nat20AffixDef def, RolledAffix roll,
                                    double minValue, double maxValue, double midValue,
                                    String rarityColor) {
        // ABILITY affixes have no targetStat — use the per-id formatter. Indestructible keeps
        // the legacy gold hue; every other ability renders white.
        if (def.type() == AffixType.ABILITY) {
            String abilityColor = "nat20:indestructible".equals(def.id())
                    ? Nat20AffixDisplay.ABILITY_GOLD : "#FFFFFF";
            return color(abilityColor, Nat20AffixDisplay.abilityLine(def.id(), midValue));
        }

        Nat20AffixDisplay.Entry entry = Nat20AffixDisplay.forTargetStat(def.targetStat());
        if (entry == null) {
            // Unmapped — fall back to the raw targetStat to make gaps visible.
            return color("#888888", "+" + String.format("%.1f", midValue) + " " + def.targetStat());
        }

        return switch (entry.format()) {
            case FLAT_DAMAGE_RANGE -> renderFlatDamageRange(entry, minValue, maxValue);
            case DOT_TOTAL_RANGE -> renderDotTotalRange(entry, minValue, maxValue, roll.duration());
            case PERCENT_BUFF -> renderPercentBuff(entry, midValue, rarityColor);
            case SCORE -> renderScore(entry, midValue, rarityColor);
            case ABILITY -> color(Nat20AffixDisplay.ABILITY_GOLD,
                    Nat20AffixDisplay.abilityLine(def.id(), midValue));
        };
    }

    private String renderFlatDamageRange(Nat20AffixDisplay.Entry entry, double minValue, double maxValue) {
        // e.g. "Adds 5-8 Fire Damage" — entire line in element colour.
        return color(entry.elementColor(),
                "Adds " + formatRange(minValue, maxValue, false) + " " + entry.displayName());
    }

    private String renderDotTotalRange(Nat20AffixDisplay.Entry entry, double minValue, double maxValue,
                                       double rolledDurationSeconds) {
        // Total damage is fixed per rolled affix (per-tick × BASE_TICKS). Shorter
        // rolled durations fit the same total into fewer ticks, so per-tick is
        // higher — shorter rolls are more valuable. BASE_TICKS comes from the
        // DOT system (currently MAX_DURATION / 2s = 7.5).
        double minTotal = minValue * Nat20AffixDisplay.DOT_BASE_TICKS;
        double maxTotal = maxValue * Nat20AffixDisplay.DOT_BASE_TICKS;
        boolean isBleed = Nat20AffixDisplay.BLEED.equals(entry.elementColor());
        StringBuilder sb = new StringBuilder()
                .append(entry.displayName()).append(": ")
                .append(formatRange(minTotal, maxTotal, false));
        if (!isBleed) {
            String element = elementForDotColor(entry.elementColor());
            if (!element.isEmpty()) sb.append(" ").append(element);
        }
        double duration = rolledDurationSeconds > 0
                ? rolledDurationSeconds : Nat20AffixDisplay.DOT_DEFAULT_DURATION_SECONDS;
        sb.append(" over ").append(formatSeconds(duration)).append("s");
        return color(entry.elementColor(), sb.toString());
    }

    private static String formatSeconds(double seconds) {
        if (Math.abs(seconds - Math.round(seconds)) < 0.05) {
            return String.valueOf((int) Math.round(seconds));
        }
        return String.format("%.1f", seconds);
    }

    private String renderPercentBuff(Nat20AffixDisplay.Entry entry, double midValue, String rarityColor) {
        // e.g. "+20% Crit Chance" — value bold in rarity (or element) colour, name in same colour.
        int pct = (int) Math.round(midValue * 100.0);
        String text = "+" + pct + "% " + entry.displayName();
        String lineColor = entry.elementColor() != null ? entry.elementColor() : rarityColor;
        return color(lineColor, text);
    }

    private String renderScore(Nat20AffixDisplay.Entry entry, double midValue, String rarityColor) {
        // e.g. "+3 STR" — whole line white, unaffected by rarity or stat colour.
        int val = (int) Math.round(midValue);
        return color("#FFFFFF", "+" + val + " " + entry.displayName());
    }

    /** Format a {@code min-max} range, collapsing to a single value when they match. */
    private String formatRange(double min, double max, boolean percent) {
        String minStr = formatNumber(min, percent);
        String maxStr = formatNumber(max, percent);
        if (minStr.equals(maxStr)) return minStr + (percent ? "%" : "");
        return minStr + "-" + maxStr + (percent ? "%" : "");
    }

    private String formatNumber(double v, boolean percent) {
        double display = percent ? v * 100.0 : v;
        long rounded = Math.round(display);
        return String.valueOf((int) rounded);
    }

    /**
     * Map a DoT entry's element colour back to its element word for the "over 20s" line.
     * (The display name is the effect name like "Ignite" / "Deep Wounds", not the element.)
     */
    private String elementForDotColor(@Nullable String color) {
        if (color == null) return "";
        return switch (color) {
            case Nat20AffixDisplay.FIRE -> "Fire";
            case Nat20AffixDisplay.FROST -> "Frost";
            case Nat20AffixDisplay.POISON -> "Poison";
            case Nat20AffixDisplay.VOID -> "Void";
            case Nat20AffixDisplay.BLEED -> "Bleed";
            default -> "";
        };
    }

    private String color(String hex, String text) {
        return "<color is=\"" + hex + "\">" + text + "</color>";
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
