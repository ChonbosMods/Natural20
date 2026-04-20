package com.chonbosmods.ui;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.EffectAffixSource;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.StatScaling;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.loot.display.Nat20AffixDisplay;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes the player's currently-applied affix totals from equipped armor
 * (hotbar weapon excluded, per design) and formats them for the Character
 * Sheet "Equipped Affixes" readout.
 *
 * <p>For each affix id encountered on the player's armor sources:
 * <ul>
 *   <li>Raw value = {@code AffixValueRange.interpolate(midLevel, ilvl, qualityValue)}
 *       (deterministic midpoint, not a fresh roll).</li>
 *   <li>Stat scaling applied per-contribution when {@link Nat20AffixDef#statScaling()}
 *       is present (matches {@code Nat20ThornsSystem} / {@code Nat20ScoreBonusSystem}
 *       pattern).</li>
 *   <li>Contributions summed across all equipped pieces.</li>
 *   <li>Softcap applied once to the sum using the same {@code k} constant the
 *       owning combat system uses (mirrored in {@link #SOFTCAP_K} below).</li>
 * </ul>
 *
 * <p>The display string and color come from {@link Nat20AffixDisplay}; ABILITY
 * and unmapped affixes are currently skipped (no combined-value semantics).
 */
public final class CharacterSheetAffixSummary {

    /** A single ready-to-render affix readout line. */
    public record Line(String text, String color) {}

    /**
     * Per-affix softcap k constants, copied from each owning combat system so
     * the readout matches the value actually applied in combat. If an affix id
     * is not present here, no softcap is applied (its raw summed value is
     * shown; e.g. SCORE affixes, flat elemental damage, DoTs, raw HP).
     */
    private static final Map<String, Double> SOFTCAP_K = Map.ofEntries(
            Map.entry("nat20:crit_chance",         0.30),
            Map.entry("nat20:crit_damage",         2.0),
            Map.entry("nat20:precision",           0.40),
            Map.entry("nat20:attack_speed",        0.35),
            Map.entry("nat20:backstab",            1.0),
            Map.entry("nat20:gallant",             0.60),
            Map.entry("nat20:crushing_blow",       0.20),
            Map.entry("nat20:life_leech",          0.20),
            Map.entry("nat20:mana_leech",          0.20),
            Map.entry("nat20:thorns",              50.0),
            Map.entry("nat20:light_foot",          0.80),
            Map.entry("nat20:block_proficiency",   0.80),
            Map.entry("nat20:resilience",          0.80),
            Map.entry("nat20:rally",               0.40),
            Map.entry("nat20:hex",                 1.0),
            Map.entry("nat20:vicious_mockery",     0.50),
            Map.entry("nat20:water_breathing",     5.0),
            Map.entry("nat20:focused_mind",        3.0),
            Map.entry("nat20:fire_resistance",     0.50),
            Map.entry("nat20:frost_resistance",    0.50),
            Map.entry("nat20:poison_resistance",   0.50),
            Map.entry("nat20:void_resistance",     0.50),
            Map.entry("nat20:physical_resistance", 0.50),
            Map.entry("nat20:evasion",             0.50),
            Map.entry("nat20:absorption",          0.50)
    );

    private CharacterSheetAffixSummary() {}

    public static List<Line> summarize(Ref<EntityStore> playerRef,
                                       Store<EntityStore> store,
                                       PlayerStats stats) {
        Nat20LootSystem loot = Natural20.getInstance().getLootSystem();
        if (loot == null) return List.of();
        Nat20AffixRegistry registry = loot.getAffixRegistry();

        List<EffectAffixSource.Source> sources =
                new ArrayList<>(EffectAffixSource.resolveDefenderSources(playerRef, store, loot));
        addOffHandShieldSources(sources, playerRef, store, loot);
        if (sources.isEmpty()) return List.of();

        Map<String, Double> rawSum = new HashMap<>();
        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolled : src.affixes()) {
                String id = rolled.id();
                Nat20AffixDef def = registry.get(id);
                if (def == null) continue;
                AffixValueRange range = def.getValuesForRarity(src.rarity());
                if (range == null) continue;

                double base = range.interpolate(rolled.midLevel(), src.ilvl(), src.qualityValue());

                StatScaling scaling = def.statScaling();
                if (scaling != null && stats != null) {
                    Stat primary = scaling.primary();
                    int modifier = stats.getPowerModifier(primary);
                    base = base * (1.0 + modifier * scaling.factor());
                }

                rawSum.merge(id, base, Double::sum);
            }
        }

        List<Line> lines = new ArrayList<>(rawSum.size());
        for (Map.Entry<String, Double> e : rawSum.entrySet()) {
            String id = e.getKey();
            double raw = e.getValue();
            Nat20AffixDef def = registry.get(id);
            if (def == null) continue;

            Nat20AffixDisplay.Entry display = Nat20AffixDisplay.forTargetStat(def.targetStat());
            if (display == null) continue; // ABILITY / unmapped — skip for now

            Double k = SOFTCAP_K.get(id);
            double effective = (k != null && raw > 0) ? (raw / (1.0 + raw / k)) : raw;

            lines.add(new Line(formatLine(display, effective), pickColor(display)));
        }

        lines.sort(Comparator.comparing(Line::text));
        return lines;
    }

    /**
     * Shields live in the player's Utility inventory (Hytale's off-hand: shield
     * JSON templates carry {@code "Utility": {"Usable": true}} and their animations
     * ship under {@code Items/Off_Handed/Shield/...}). Nat20 pools shield affixes
     * under "armor" (block proficiency, resistances, evasion, gallant, thorns),
     * so the readout should include the off-hand shield alongside true armor.
     */
    @SuppressWarnings("unchecked")
    private static void addOffHandShieldSources(List<EffectAffixSource.Source> sources,
                                                Ref<EntityStore> playerRef,
                                                Store<EntityStore> store,
                                                Nat20LootSystem loot) {
        CombinedItemContainer utility = InventoryComponent.getCombined(
                store, playerRef, new ComponentType[]{InventoryComponent.Utility.getComponentType()});
        if (utility == null) return;
        for (short slot = 0; slot < utility.getCapacity(); slot++) {
            ItemStack item = utility.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;
            if (!isShield(item.getItemId())) continue;
            Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) continue;
            sources.add(EffectAffixSource.sourceFromLootData(lootData, loot));
        }
    }

    private static boolean isShield(String itemId) {
        if (itemId == null) return false;
        String local = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return local.toLowerCase(Locale.US).startsWith("weapon_shield");
    }

    private static String formatLine(Nat20AffixDisplay.Entry e, double v) {
        return switch (e.format()) {
            case PERCENT_BUFF -> "+" + fmt1(v * 100.0) + "% " + e.displayName();
            case SCORE -> "+" + (int) Math.round(v) + " " + e.displayName();
            case FLAT_DAMAGE_RANGE -> "+" + fmt1(v) + " " + e.displayName();
            case DOT_TOTAL_RANGE -> "+" + fmt1(v * Nat20AffixDisplay.DOT_BASE_TICKS) + " " + e.displayName();
            case ABILITY -> e.displayName();
        };
    }

    private static String pickColor(Nat20AffixDisplay.Entry e) {
        if (e.elementColor() != null) return e.elementColor();
        if (e.statColor() != null) return e.statColor().color();
        return Nat20AffixDisplay.WHITE;
    }

    private static String fmt1(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
