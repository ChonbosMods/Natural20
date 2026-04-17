package com.chonbosmods.loot.mob;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Heuristic ilvl tier + gear-category resolver for Hytale item IDs.
 * Used by {@link Nat20MobLootListener} to filter a mob's drop list to items
 * appropriate for its area-level: a low-mlvl mob shouldn't drop a Mithril sword,
 * a high-mlvl mob shouldn't drop a Copper axe.
 *
 * Tier bands are min/max ilvl windows. An item drops only if the mob's ilvl
 * falls within its material's window. Items with no recognized material token
 * (e.g., Sap, Wool, Feedbag) are treated as tier-agnostic crafting drops and
 * not classified as gear here (category returns null).
 */
public final class Nat20ItemTierResolver {

    /**
     * Material token (lowercased substring of itemId) → {minIlvl, maxIlvl}.
     * Iterated in insertion order; first match wins, so more-specific tokens
     * (silversteel before silver, adamantite before iron — "adamant" wouldn't
     * collide but the pattern matters if material names share substrings) come first.
     *
     * Windows overlap by several ilvl across adjacent tiers so a mob can pull
     * from multiple tiers for "lucky time" rolls: an ilvl-15 mob can drop
     * bronze, iron, or steel items, not just iron.
     */
    private static final Map<String, int[]> MATERIAL_ILVL;
    static {
        MATERIAL_ILVL = new LinkedHashMap<>();
        // Endgame specials (unique-material weapons from late-game content)
        MATERIAL_ILVL.put("onyxium",      new int[]{30, 45});
        MATERIAL_ILVL.put("adamantite",   new int[]{28, 45});
        MATERIAL_ILVL.put("thorium",      new int[]{26, 45});
        MATERIAL_ILVL.put("runic",        new int[]{22, 45});
        MATERIAL_ILVL.put("nexus",        new int[]{22, 45});
        MATERIAL_ILVL.put("doomed",       new int[]{22, 45});
        MATERIAL_ILVL.put("prisma",       new int[]{22, 45});
        MATERIAL_ILVL.put("incandescent", new int[]{22, 45});
        MATERIAL_ILVL.put("frost",        new int[]{22, 45});
        MATERIAL_ILVL.put("ancient",      new int[]{22, 45});
        MATERIAL_ILVL.put("cindercloth",  new int[]{22, 40});
        // Mid-high tier (silver-mithril band)
        MATERIAL_ILVL.put("silversteel",  new int[]{22, 38});
        MATERIAL_ILVL.put("mithril",      new int[]{22, 38});
        MATERIAL_ILVL.put("silver",       new int[]{22, 38});
        // Mid tier
        MATERIAL_ILVL.put("cobalt",       new int[]{16, 32});
        MATERIAL_ILVL.put("steel",        new int[]{16, 32});
        // Iron tier
        MATERIAL_ILVL.put("iron",         new int[]{8, 26});
        // Early tier
        MATERIAL_ILVL.put("bronze",       new int[]{3, 20});
        MATERIAL_ILVL.put("copper",       new int[]{3, 20});
        // Low tier (no-material-ore items)
        MATERIAL_ILVL.put("leather",      new int[]{1, 16});
        MATERIAL_ILVL.put("cloth",        new int[]{1, 14});
        MATERIAL_ILVL.put("wool",         new int[]{1, 14});
        MATERIAL_ILVL.put("wood",         new int[]{1, 14});
        MATERIAL_ILVL.put("bark",         new int[]{1, 14});
        MATERIAL_ILVL.put("flint",        new int[]{1, 14});
        MATERIAL_ILVL.put("stone",        new int[]{1, 14});
        MATERIAL_ILVL.put("bone",         new int[]{1, 14});
        MATERIAL_ILVL.put("crude",        new int[]{1, 12});
        MATERIAL_ILVL.put("scrap",        new int[]{1, 12});
        MATERIAL_ILVL.put("rusty",        new int[]{1, 12});
    }

    private Nat20ItemTierResolver() {}

    /**
     * True if the item's material tier is appropriate for the given ilvl.
     * Items without a recognized material token return true (no gating).
     */
    public static boolean allowsIlvl(String itemId, int ilvl) {
        if (itemId == null) return false;
        String lower = itemId.toLowerCase();
        for (var entry : MATERIAL_ILVL.entrySet()) {
            if (lower.contains(entry.getKey())) {
                int[] range = entry.getValue();
                return ilvl >= range[0] && ilvl <= range[1];
            }
        }
        return true;
    }

    /**
     * Infer the Nat20 loot category key from an item ID prefix, or null
     * if the item is not a gear item (so it shouldn't receive Nat20 affixes).
     */
    public static String inferCategory(String itemId) {
        if (itemId == null) return null;
        // Strip "Hytale:" or other namespace
        String local = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        if (local.startsWith("Armor_")) return "armor";
        if (local.startsWith("Tool_")) return "tool";
        if (local.startsWith("Weapon_")) {
            String lower = local.toLowerCase();
            // Shield is categorized as 'armor' for Nat20 affix matching:
            // block proficiency, resistances, evasion, gallant, thorns all apply
            // to shields in the same pool as other armor pieces.
            if (lower.startsWith("weapon_shield")) return "armor";
            if (lower.contains("_bow") || lower.contains("_crossbow") || lower.contains("_shortbow")
                    || lower.contains("_longbow") || lower.contains("_gun") || lower.contains("_handgun")
                    || lower.contains("_blowgun") || lower.contains("_staff") || lower.contains("_wand")
                    || lower.contains("_spellbook")) {
                return "ranged_weapon";
            }
            return "melee_weapon";
        }
        return null;
    }

    public static boolean isGearItem(String itemId) {
        return inferCategory(itemId) != null;
    }
}
