package com.chonbosmods.background;

/**
 * One item in a Background's starter kit.
 *
 * @param itemId    a loot-registry item id (e.g., "Weapon_Sword_Crude").
 *                  Must resolve in {@code Nat20LootEntryRegistry} at grant time.
 * @param quantity  stack size; must be > 0.
 * @param rollAffix if true, the item is rolled through {@code AffixRewardRoller.rollFor}
 *                  with a Common-tier affix; if false, it is granted as a plain stack
 *                  with no affix (used for arrows: a "+5% crit chance arrow" reads wrong).
 */
public record KitItem(String itemId, int quantity, boolean rollAffix) {
    public KitItem {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be null or blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0 (got " + quantity + ")");
        }
    }
}
