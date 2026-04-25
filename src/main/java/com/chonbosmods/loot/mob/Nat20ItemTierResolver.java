package com.chonbosmods.loot.mob;

import com.chonbosmods.loot.filter.Nat20GearFilter;

import java.util.Optional;

/**
 * Gear-category resolver and ilvl-gating facade for Hytale item IDs.
 * Category inference (armor / tool / melee_weapon / ranged_weapon) is heuristic
 * on the item ID prefix; ilvl gating is delegated to {@link Nat20GearFilter},
 * which is loaded once at startup by {@code Nat20LootSystem} and installed via
 * {@link #setFilter(Nat20GearFilter)}.
 */
public final class Nat20ItemTierResolver {

    private Nat20ItemTierResolver() {}

    /** Set by Nat20LootSystem at startup. */
    private static volatile Nat20GearFilter filter;

    public static void setFilter(Nat20GearFilter f) {
        filter = f;
    }

    public static boolean allowsIlvl(String itemId, int ilvl) {
        Nat20GearFilter f = filter;
        return f != null && f.isAllowed(itemId, ilvl);
    }

    /**
     * Resolve an item's tier band + category through the gear filter. Unlike
     * {@link #inferCategory(String)}, this honors allowlist entries with explicit
     * categories, so mod-namespaced items (e.g. {@code SomeMod:Plasma_Sword})
     * resolve correctly. Returns empty when no filter is installed or the item
     * is not recognized by the filter (blocklisted or no matching token/override).
     */
    public static Optional<Nat20GearFilter.TierResolution> resolve(String itemId) {
        Nat20GearFilter f = filter;
        return f == null ? Optional.empty() : f.resolveTier(itemId);
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
