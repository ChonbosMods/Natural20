package com.chonbosmods.loot.display;

import com.chonbosmods.stats.Stat;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Locked display-name mapping for affix tooltip lines.
 *
 * <p>The keys are {@code Nat20AffixDef.targetStat} for EFFECT/STAT affixes and
 * {@code Nat20AffixDef.id} for ABILITY affixes (which have no targetStat). Each entry
 * determines how a rolled affix renders on the tooltip.
 *
 * <p>Formats follow the specification locked in conversation (2026-04-14):
 * no stat-scaling parentheticals, no minus signs, flat damage and DoT shown as
 * {@code min-max} ranges (collapsed to a single value when {@code min == max}).
 */
public final class Nat20AffixDisplay {

    private Nat20AffixDisplay() {}

    // Element colors (§8 of AFFIX_TOOLTIP_DESIGN.md)
    public static final String FIRE = "#FF6622";
    public static final String FROST = "#66CCFF";
    public static final String POISON = "#66FF44";
    public static final String VOID = "#AA44DD";
    public static final String BLEED = "#CC2222";
    public static final String ABILITY_GOLD = "#bb8a2c";
    public static final String MANA_BLUE = "#4488FF";
    public static final String CRIT_YELLOW = "#FFDD44";
    public static final String WHITE = "#FFFFFF";

    // DoT constants — all DoTs share the same tick cadence for now.
    public static final int DOT_TICK_COUNT = 10;
    public static final int DOT_DURATION_SECONDS = 20;

    public enum Format {
        /** {@code "5-8 Fire Damage"} — no {@code +}, element colored, range. */
        FLAT_DAMAGE_RANGE,
        /** {@code "Ignite: 60-100 Fire over 20s"} — element colored, per-tick × 10. */
        DOT_TOTAL_RANGE,
        /** {@code "+20% Crit Chance"} — rarity or element colored, fixed midpoint. */
        PERCENT_BUFF,
        /** {@code "+3 STR"} — value bold in rarity color, stat name in stat color, fixed midpoint. */
        SCORE,
        /** ABILITY affixes — per-id custom formatter. */
        ABILITY
    }

    public record Entry(String displayName, Format format, @Nullable String elementColor,
                        @Nullable Stat statColor) {
        public static Entry flatDamage(String name, String color) {
            return new Entry(name, Format.FLAT_DAMAGE_RANGE, color, null);
        }
        public static Entry dot(String name, String elementColor) {
            return new Entry(name, Format.DOT_TOTAL_RANGE, elementColor, null);
        }
        public static Entry percent(String name) {
            return new Entry(name, Format.PERCENT_BUFF, null, null);
        }
        public static Entry percentEl(String name, String color) {
            return new Entry(name, Format.PERCENT_BUFF, color, null);
        }
        public static Entry score(String name, Stat stat) {
            return new Entry(name, Format.SCORE, null, stat);
        }
    }

    // --- targetStat → display entry ---

    private static final Map<String, Entry> BY_TARGET_STAT = Map.ofEntries(
            // Ability scores
            Map.entry("Score_STR", Entry.score("STR", Stat.STR)),
            Map.entry("Score_DEX", Entry.score("DEX", Stat.DEX)),
            Map.entry("Score_CON", Entry.score("CON", Stat.CON)),
            Map.entry("Score_INT", Entry.score("INT", Stat.INT)),
            Map.entry("Score_WIS", Entry.score("WIS", Stat.WIS)),
            Map.entry("Score_CHA", Entry.score("CHA", Stat.CHA)),

            // Flat elemental damage (range, no +, "Damage" suffix)
            Map.entry("FireDamage",   Entry.flatDamage("Fire Damage",   FIRE)),
            Map.entry("FrostDamage",  Entry.flatDamage("Frost Damage",  FROST)),
            Map.entry("PoisonDamage", Entry.flatDamage("Poison Damage", POISON)),
            Map.entry("VoidDamage",   Entry.flatDamage("Void Damage",   VOID)),
            Map.entry("ThornsDamage", Entry.flatDamage("Thorns Damage", BLEED)),

            // DoT totals — display name is the conjugated verb the weapon performs on the target.
            Map.entry("IgniteDamage",  new Entry("Ignites",  Format.DOT_TOTAL_RANGE, FIRE,   null)),
            Map.entry("ColdDamage",    new Entry("Chills",   Format.DOT_TOTAL_RANGE, FROST,  null)),
            Map.entry("InfectDamage",  new Entry("Infects",  Format.DOT_TOTAL_RANGE, POISON, null)),
            Map.entry("CorruptDamage", new Entry("Corrupts", Format.DOT_TOTAL_RANGE, VOID,   null)),
            Map.entry("BleedDamage",   new Entry("Bleeds",   Format.DOT_TOTAL_RANGE, BLEED,  null)),

            // Resistances (element-colored; physical lives in the utility block below)
            Map.entry("FireResistance",     Entry.percentEl("Fire Resistance",   FIRE)),
            Map.entry("FrostResistance",    Entry.percentEl("Frost Resistance",  FROST)),
            Map.entry("PoisonResistance",   Entry.percentEl("Poison Resistance", POISON)),
            Map.entry("VoidResistance",     Entry.percentEl("Void Resistance",   VOID)),

            // Elemental vulnerabilities (positive value; display name per D&D 5e terminology)
            Map.entry("FireWeakness",   Entry.percentEl("Fire Vulnerability",   FIRE)),
            Map.entry("FrostWeakness",  Entry.percentEl("Frost Vulnerability",  FROST)),
            Map.entry("PoisonWeakness", Entry.percentEl("Poison Vulnerability", POISON)),
            Map.entry("VoidWeakness",   Entry.percentEl("Void Vulnerability",   VOID)),

            // Combat modifiers (fixed %)
            Map.entry("Nat20CritChance",   Entry.percentEl("Crit Chance",      CRIT_YELLOW)),
            Map.entry("Nat20CritDamage",   Entry.percentEl("Crit Damage",      CRIT_YELLOW)),
            Map.entry("ArmorPenetration",  Entry.percentEl("Precision",        WHITE)),
            Map.entry("Backstab",          Entry.percentEl("Backstab",         BLEED)),
            Map.entry("Evasion",           Entry.percentEl("Evasion",          Stat.DEX.color())),
            Map.entry("Gallant",           Entry.percentEl("Gallant",          VOID)),
            Map.entry("CrushingBlow",      Entry.percentEl("Crushing Blow",    BLEED)),
            Map.entry("LifeLeech",         Entry.percentEl("Life Leech",       BLEED)),
            Map.entry("ManaLeech",         Entry.percentEl("Mana Leech",       MANA_BLUE)),
            Map.entry("BlockProficiency",  Entry.percentEl("Block Proficiency", WHITE)),

            // Utility (fixed %)
            Map.entry("DamageAbsorption", Entry.percentEl("Absorption",        MANA_BLUE)),
            Map.entry("ManaRegen",        Entry.percentEl("Focused Mind",      MANA_BLUE)),
            Map.entry("WaterBreathing",   Entry.percentEl("Water Breathing",   MANA_BLUE)),
            Map.entry("Rally",            Entry.percentEl("Rally",             CRIT_YELLOW)),
            Map.entry("HexDamage",        Entry.percentEl("Hex",               VOID)),
            Map.entry("LightFoot",        Entry.percentEl("Lightweight",       Stat.DEX.color())),
            Map.entry("Resilience",       Entry.percentEl("Resilience",        WHITE)),
            Map.entry("ViciousMockery",   Entry.percentEl("Vicious Mockery",   BLEED)),
            Map.entry("PhysicalResistance", Entry.percentEl("Physical Resistance", WHITE)),

            // Attack speed — Hytale's internal stat name is InteractionSpeed.
            Map.entry("InteractionSpeed", Entry.percentEl("Attack Speed",      WHITE))
    );

    /** Lookup a display entry for a given targetStat. Returns {@code null} for ABILITY affixes
     *  and unmapped stats. */
    @Nullable
    public static Entry forTargetStat(@Nullable String targetStat) {
        if (targetStat == null) return null;
        return BY_TARGET_STAT.get(targetStat);
    }

    /**
     * Render an ABILITY affix line. These don't have a targetStat; each one has a unique shape
     * driven by its affix id.
     *
     * @param affixId        e.g., {@code "nat20:delve"}
     * @param midFlatValue   range.interpolate(midLevel) — single midpoint value
     * @return formatted string (no color markup; caller wraps in ability-gold color)
     */
    public static String abilityLine(String affixId, double midFlatValue) {
        int intVal = (int) Math.round(midFlatValue);
        int pctVal = (int) Math.round(midFlatValue * 100.0);
        return switch (affixId) {
            case "nat20:delve"          -> "Delve: 1\u00d7" + intVal + " Line";
            case "nat20:fissure"        -> "Fissure: " + intVal + "-Wide Strip";
            case "nat20:rend"           -> "Rend: " + intVal + "-Tall Strip";
            case "nat20:quake"          -> "Quake: " + intVal + "\u00d7" + intVal + " Area";
            case "nat20:resonance"      -> "+" + intVal + " Resonance";
            case "nat20:telekinesis"    -> "+" + intVal + " Telekinesis";
            case "nat20:indestructible" -> "Indestructible";
            case "nat20:haste"          -> "+" + pctVal + "% Haste";
            case "nat20:fortified"      -> "+" + pctVal + "% Fortified";
            default -> affixId;
        };
    }
}
