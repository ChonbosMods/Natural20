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

    /** Material token (lowercased substring of itemId) → {minIlvl, maxIlvl}. */
    private static final Map<String, int[]> MATERIAL_ILVL;
    static {
        MATERIAL_ILVL = new LinkedHashMap<>();
        MATERIAL_ILVL.put("onyxium",    new int[]{34, 45});
        MATERIAL_ILVL.put("adamantite", new int[]{32, 45});
        MATERIAL_ILVL.put("thorium",    new int[]{30, 45});
        MATERIAL_ILVL.put("mithril",    new int[]{24, 36});
        MATERIAL_ILVL.put("silver",     new int[]{24, 36});
        MATERIAL_ILVL.put("cobalt",     new int[]{18, 30});
        MATERIAL_ILVL.put("steel",      new int[]{18, 30});
        MATERIAL_ILVL.put("iron",       new int[]{10, 22});
        MATERIAL_ILVL.put("bronze",     new int[]{5, 15});
        MATERIAL_ILVL.put("copper",     new int[]{5, 15});
        MATERIAL_ILVL.put("leather",    new int[]{1, 12});
        MATERIAL_ILVL.put("cloth",      new int[]{1, 10});
        MATERIAL_ILVL.put("wool",       new int[]{1, 10});
        MATERIAL_ILVL.put("bark",       new int[]{1, 8});
        MATERIAL_ILVL.put("flint",      new int[]{1, 8});
        MATERIAL_ILVL.put("stone",      new int[]{1, 8});
        MATERIAL_ILVL.put("wood",       new int[]{1, 8});
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
