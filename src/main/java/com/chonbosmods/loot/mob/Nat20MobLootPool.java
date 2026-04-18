package com.chonbosmods.loot.mob;

import com.chonbosmods.loot.registry.Nat20LootEntryRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Per-kill gear-pool snapshot for the Nat20 mob-loot listener.
 *
 * <p>Holds two filtered pools (both restricted to gear items within the mob's
 * ilvl material band):
 * <ul>
 *   <li><b>global</b> — every registered Nat20 gear item from
 *       {@link Nat20LootEntryRegistry#getAllItemIds()}.</li>
 *   <li><b>native_</b> — the mob role's {@link ItemDropList}, same filter applied.
 *       Empty when the mob has no gear or every native item sits outside the
 *       ilvl band.</li>
 * </ul>
 *
 * <p>{@link #pick(Random)} rolls per slot: {@link #NATIVE_LIST_BIAS} probability
 * of pulling from native, otherwise global. Falls through to global when native
 * is empty so callers never need to branch.
 *
 * <p>Tools (Tool_ prefix) are intentionally included: one of the few ways to
 * obtain affix-bonus tools.
 */
public final class Nat20MobLootPool {

    public static final float NATIVE_LIST_BIAS = 0.08f;

    public enum Source { NATIVE, GLOBAL }
    public record PickResult(String itemId, Source source) {}

    private final List<String> global;
    private final List<String> native_;

    private Nat20MobLootPool(List<String> global, List<String> native_) {
        this.global = global;
        this.native_ = native_;
    }

    /**
     * Build a pool snapshot for a single mob death. Both pools are filtered
     * by {@link Nat20ItemTierResolver#isGearItem(String)} and
     * {@link Nat20ItemTierResolver#allowsIlvl(String, int)}.
     */
    public static Nat20MobLootPool build(Ref<EntityStore> mobRef,
                                          Store<EntityStore> store,
                                          int ilvl,
                                          Nat20LootEntryRegistry registry) {
        List<String> global = buildGlobalPool(registry, ilvl);
        List<String> native_ = buildNativePool(mobRef, store, ilvl);
        return new Nat20MobLootPool(global, native_);
    }

    public boolean isEmpty() { return global.isEmpty() && native_.isEmpty(); }
    public int globalSize()  { return global.size(); }
    public int nativeSize()  { return native_.size(); }

    /**
     * Pick one item for a drop slot. 8% chance to pull from native (if non-empty),
     * otherwise from global. Caller must handle a null return only if the whole
     * pool is empty (checked via {@link #isEmpty()} first).
     */
    public PickResult pick(Random rng) {
        if (!native_.isEmpty() && rng.nextFloat() < NATIVE_LIST_BIAS) {
            return new PickResult(native_.get(rng.nextInt(native_.size())), Source.NATIVE);
        }
        if (global.isEmpty()) {
            if (native_.isEmpty()) return null;
            return new PickResult(native_.get(rng.nextInt(native_.size())), Source.NATIVE);
        }
        return new PickResult(global.get(rng.nextInt(global.size())), Source.GLOBAL);
    }

    private static List<String> buildGlobalPool(Nat20LootEntryRegistry registry, int ilvl) {
        List<String> pool = new ArrayList<>();
        for (String itemId : registry.getAllItemIds()) {
            if (!Nat20ItemTierResolver.isGearItem(itemId)) continue;
            if (!Nat20ItemTierResolver.allowsIlvl(itemId, ilvl)) continue;
            pool.add(itemId);
        }
        return pool;
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
