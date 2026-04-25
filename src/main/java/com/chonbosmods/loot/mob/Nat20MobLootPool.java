package com.chonbosmods.loot.mob;

import com.chonbosmods.loot.CategoryWeightedPicker;
import com.chonbosmods.loot.filter.Nat20GearFilter;
import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Per-kill gear-pool snapshot for the Nat20 mob-loot listener.
 *
 * <p>Holds a category-bucketed global pool plus the mob role's native drop list
 * (both restricted to gear items in the mob's ilvl band). The global pool is
 * keyed by the categories declared in {@link CategoryWeightedPicker#WEIGHTS}
 * (melee_weapon, armor, ranged_weapon, tool); selection delegates to
 * {@link CategoryWeightedPicker#pick} for category-weighted random sampling.
 *
 * <p>{@link #pick(Random)} rolls {@link #NATIVE_LIST_BIAS} (5%) for the native
 * list when non-empty, otherwise samples the bucketed global pool. Falls
 * through to native when global is empty so callers never need to branch.
 *
 * <p>Tools (Tool_ prefix) are intentionally included: one of the few ways to
 * obtain affix-bonus tools.
 */
public final class Nat20MobLootPool {

    public static final float NATIVE_LIST_BIAS = 0.05f;

    public enum Source { NATIVE, GLOBAL }
    public record PickResult(String itemId, Source source) {}

    private final Map<String, List<String>> buckets;
    private final List<String> native_;

    private Nat20MobLootPool(Map<String, List<String>> buckets, List<String> native_) {
        this.buckets = buckets;
        this.native_ = native_;
    }

    /** Test factory: bypasses EntityStore + registry. Package-private. */
    static Nat20MobLootPool forTesting(Map<String, List<String>> buckets, List<String> native_) {
        return new Nat20MobLootPool(buckets, native_);
    }

    /**
     * Build a pool snapshot for a single mob death. The global buckets and
     * native list are filtered by {@link Nat20ItemTierResolver#isGearItem(String)}
     * and {@link Nat20ItemTierResolver#allowsIlvl(String, int)}.
     */
    public static Nat20MobLootPool build(Ref<EntityStore> mobRef,
                                          Store<EntityStore> store,
                                          int ilvl,
                                          Nat20LootEntryRegistry registry) {
        Map<String, List<String>> b = buildGlobalBuckets(registry, ilvl);
        List<String> native_ = buildNativePool(mobRef, store, ilvl);
        return new Nat20MobLootPool(b, native_);
    }

    public boolean isEmpty() {
        if (!native_.isEmpty()) return false;
        for (List<String> bucket : buckets.values()) {
            if (!bucket.isEmpty()) return false;
        }
        return true;
    }

    public int globalSize() {
        int n = 0;
        for (List<String> bucket : buckets.values()) n += bucket.size();
        return n;
    }

    public int nativeSize() { return native_.size(); }

    /**
     * Pick one item for a drop slot. {@link #NATIVE_LIST_BIAS} chance to pull
     * from native (if non-empty), otherwise delegates to
     * {@link CategoryWeightedPicker#pick(Map, Random)} for the category-weighted
     * global sample. Caller must handle a null return only if the whole pool is
     * empty (checked via {@link #isEmpty()} first).
     */
    public PickResult pick(Random rng) {
        if (!native_.isEmpty() && rng.nextFloat() < NATIVE_LIST_BIAS) {
            return new PickResult(native_.get(rng.nextInt(native_.size())), Source.NATIVE);
        }
        String pick = CategoryWeightedPicker.pick(buckets, rng);
        if (pick == null) {
            if (!native_.isEmpty()) {
                return new PickResult(native_.get(rng.nextInt(native_.size())), Source.NATIVE);
            }
            return null;
        }
        return new PickResult(pick, Source.GLOBAL);
    }

    /**
     * Build the category-bucketed global gear pool. Each registered Nat20 gear
     * item is resolved through {@link Nat20ItemTierResolver#resolve(String)},
     * which routes through the gear filter so allowlist entries with explicit
     * categories (e.g. mod-namespaced items like {@code SomeMod:Plasma_Sword})
     * land in the correct bucket. Items rejected by the filter or outside the
     * resolved ilvl band are skipped.
     */
    public static Map<String, List<String>> buildGlobalBuckets(Nat20LootEntryRegistry registry, int ilvl) {
        Map<String, List<String>> b = new HashMap<>();
        for (String cat : CategoryWeightedPicker.WEIGHTS.keySet()) b.put(cat, new ArrayList<>());
        for (String itemId : registry.getAllItemIds()) {
            Optional<Nat20GearFilter.TierResolution> tier = Nat20ItemTierResolver.resolve(itemId);
            if (tier.isEmpty()) continue;
            Nat20GearFilter.IlvlBand band = tier.get().ilvlBand();
            if (ilvl < band.min() || ilvl > band.max()) continue;
            List<String> bucket = b.get(tier.get().category());
            // null guard: filter category outside our 4-bucket WEIGHTS set
            if (bucket != null) bucket.add(itemId);
        }
        return b;
    }

    private static List<String> buildNativePool(Ref<EntityStore> mobRef,
                                                 Store<EntityStore> store, int ilvl) {
        NPCEntity npc = store.getComponent(mobRef, NPCEntity.getComponentType());
        if (npc == null) return List.of();
        Role role = npc.getRole();
        if (role == null) return List.of();

        String dropListId = role.getDropListId();
        if (dropListId == null || dropListId.isEmpty()) return List.of();

        ItemDropList dropList = ItemDropList.getAssetMap().getAsset(dropListId);
        if (dropList == null || dropList.getContainer() == null) return List.of();

        List<ItemDrop> allDrops = dropList.getContainer().getAllDrops(new ArrayList<>());
        List<String> pool = new ArrayList<>();
        for (ItemDrop drop : allDrops) {
            String itemId = drop.getItemId();
            if (!Nat20ItemTierResolver.isGearItem(itemId)) continue;
            if (!Nat20ItemTierResolver.allowsIlvl(itemId, ilvl)) continue;
            pool.add(itemId);
        }
        return pool;
    }
}
