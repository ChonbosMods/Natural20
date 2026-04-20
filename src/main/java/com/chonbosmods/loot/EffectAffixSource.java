package com.chonbosmods.loot;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.mob.Nat20MobAffixes;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.Nat20MobLevel;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified attacker/defender affix resolution. Combat systems used to read
 * {@link Nat20LootData} from the player's equipped weapon or armor; they
 * now call these helpers and get the same affix list whether the entity
 * is a player (gear source) or a mob (applied-affix component source).
 *
 * Each returned {@link Source} carries the rarity + ilvl + qualityValue
 * context needed to interpolate {@link com.chonbosmods.loot.def.AffixValueRange}.
 */
public final class EffectAffixSource {

    private EffectAffixSource() {}

    /**
     * Bundle of affixes with the scaling context needed to compute final values.
     *
     * @param affixes      rolled affix entries on this source (player weapon, armor piece, or mob)
     * @param rarity       rarity key ("Common" / "Uncommon" / ... / "Legendary") used to index ValuesPerRarity
     * @param ilvl         item level (player gear) or mob area_level
     * @param qualityValue 1..5, for the ilvl+quality scaling formula
     */
    public record Source(List<RolledAffix> affixes, String rarity, int ilvl, int qualityValue) {}

    /**
     * Attacker-side affixes (weapons on players, all mob affixes on mobs).
     * Returns an empty list if the attacker has no relevant source.
     */
    public static List<Source> resolveAttackerSources(Ref<EntityStore> ref, Store<EntityStore> store,
                                                     Nat20LootSystem lootSystem) {
        Player attackerPlayer = store.getComponent(ref, Player.getComponentType());
        if (attackerPlayer != null) {
            ItemStack weapon = InventoryComponent.getItemInHand(store, ref);
            if (weapon == null || weapon.isEmpty()) return List.of();
            Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) return List.of();
            return List.of(sourceFromLootData(lootData, lootSystem));
        }
        return mobSource(ref, store);
    }

    /**
     * Defender-side affixes (all equipped armor on players, all mob affixes on mobs).
     * Returns an empty list if the defender has no relevant source.
     */
    @SuppressWarnings("unchecked")
    public static List<Source> resolveDefenderSources(Ref<EntityStore> ref, Store<EntityStore> store,
                                                     Nat20LootSystem lootSystem) {
        Player defenderPlayer = store.getComponent(ref, Player.getComponentType());
        if (defenderPlayer != null) {
            CombinedItemContainer armor = InventoryComponent.getCombined(
                    store, ref, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
            if (armor == null) return List.of();
            List<Source> out = new ArrayList<>();
            for (short slot = 0; slot < armor.getCapacity(); slot++) {
                ItemStack item = armor.getItemStack(slot);
                if (item == null || item.isEmpty()) continue;
                Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
                if (lootData == null) continue;
                out.add(sourceFromLootData(lootData, lootSystem));
            }
            return out;
        }
        return mobSource(ref, store);
    }

    public static Source sourceFromLootData(Nat20LootData lootData, Nat20LootSystem lootSystem) {
        int qv = Nat20AffixScaling.qualityValueOf(lootData, lootSystem.getRarityRegistry());
        return new Source(lootData.getAffixes(), lootData.getRarity(), lootData.getItemLevel(), qv);
    }

    /** Returns a single-element list with the mob's applied affixes, or empty if none. */
    private static List<Source> mobSource(Ref<EntityStore> ref, Store<EntityStore> store) {
        Nat20MobAffixes affixes = store.getComponent(ref, Natural20.getMobAffixesType());
        if (affixes == null || affixes.isEmpty()) return List.of();
        Nat20MobLevel level = store.getComponent(ref, Natural20.getMobLevelType());
        DifficultyTier difficulty = level != null ? level.getDifficultyTier() : null;
        if (difficulty == null) return List.of();
        int ilvl = level != null ? level.getAreaLevel() : 10;
        return List.of(new Source(affixes.getAffixes(),
                rarityKeyFor(difficulty), ilvl, qualityValueFor(difficulty)));
    }

    /** DifficultyTier → "Uncommon"/"Rare"/"Epic"/"Legendary" key. */
    public static String rarityKeyFor(DifficultyTier difficulty) {
        return switch (difficulty) {
            case UNCOMMON -> "Uncommon";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Legendary";
        };
    }

    /** Numeric quality value used by AffixValueRange.interpolate ilvl+quality scaling. */
    public static int qualityValueFor(DifficultyTier difficulty) {
        return switch (difficulty) {
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5;
        };
    }
}
