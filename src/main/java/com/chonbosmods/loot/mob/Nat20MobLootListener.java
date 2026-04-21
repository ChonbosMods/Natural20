package com.chonbosmods.loot.mob;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootPipeline;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.Nat20MobLevel;
import com.chonbosmods.progression.Tier;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates Nat20 loot drops on mob death.
 *
 * <p>Drop count comes from {@link Nat20MobDropCount} keyed by {@link Tier} +
 * {@link DifficultyTier}. Each drop slot samples {@link Nat20MobLootPool}, which
 * returns an item from the mob's native drop list (8% bias) or from the global
 * gear pool filtered to the mob's ilvl band. Sampled items run through
 * {@link Nat20LootPipeline} for rarity/affix/name rolls and spawn via
 * {@link ItemUtils#throwItem}.
 */
public class Nat20MobLootListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobLootListener");
    private static final float DROP_THROW_SPEED = 0.0f;

    private final Nat20LootSystem lootSystem;

    public Nat20MobLootListener(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    public List<Nat20LootData> onMobDeath(Ref<EntityStore> mobRef, Store<EntityStore> store,
                                           CommandBuffer<EntityStore> commandBuffer) {
        Nat20MobLevel level = store.getComponent(mobRef, Natural20.getMobLevelType());
        if (level == null) return List.of();

        Tier tier = level.getTier();
        DifficultyTier difficulty = level.getDifficultyTier();
        int ilvl = level.getAreaLevel();

        Random rng = ThreadLocalRandom.current();
        int dropCount = Nat20MobDropCount.roll(tier, difficulty, rng);
        LOGGER.atInfo().log("onMobDeath: mob=%s tier=%s difficulty=%s ilvl=%d rolledDrops=%d",
                mobRef, tier, difficulty, ilvl, dropCount);
        if (dropCount <= 0) return List.of();

        Nat20MobLootPool pool = Nat20MobLootPool.build(
                mobRef, store, ilvl, lootSystem.getLootEntryRegistry());
        if (pool.isEmpty()) {
            LOGGER.atWarning().log("Empty loot pool for mob %s at ilvl=%d; skipping Nat20 drops",
                    mobRef, ilvl);
            return List.of();
        }

        List<GeneratedDrop> drops = generateDrops(pool, dropCount, difficulty, ilvl, rng);
        if (drops.isEmpty()) return List.of();

        for (GeneratedDrop drop : drops) {
            ItemStack stack = buildItemStack(drop.baseItemId(), drop.data());
            if (stack == null) continue;
            ItemUtils.throwItem(mobRef, stack, DROP_THROW_SPEED, commandBuffer);
        }

        LOGGER.atInfo().log("Mob %s death drops: count=%d tier=%s difficulty=%s ilvl=%d poolSizes=global=%d,native=%d",
                mobRef, drops.size(), tier, difficulty, ilvl, pool.globalSize(), pool.nativeSize());
        List<Nat20LootData> result = new ArrayList<>(drops.size());
        for (GeneratedDrop drop : drops) result.add(drop.data());
        return result;
    }

    private record GeneratedDrop(String baseItemId, Nat20LootData data) {}

    /**
     * Generate exactly {@code dropCount} drops. Each slot samples {@link Nat20MobLootPool}
     * once; whatever rarity the pipeline rolls is the drop. Null/empty picks and pipeline
     * failures skip the slot (no re-roll).
     */
    private List<GeneratedDrop> generateDrops(Nat20MobLootPool pool, int dropCount,
                                               @Nullable DifficultyTier difficulty,
                                               int ilvl, Random rng) {
        Nat20LootPipeline pipeline = lootSystem.getPipeline();
        List<GeneratedDrop> results = new ArrayList<>(dropCount);

        int[] gate = Nat20LootPipeline.rarityGateForIlvl(ilvl);
        int effectiveMin = gate[0];
        int effectiveMax = gate[1];

        for (int slot = 1; slot <= dropCount; slot++) {
            Nat20MobLootPool.PickResult pick = pool.pick(rng);
            if (pick == null) {
                LOGGER.atWarning().log("Pool pick returned null at slot %d/%d; skipping", slot, dropCount);
                continue;
            }
            String itemId = pick.itemId();
            String categoryKey = Nat20ItemTierResolver.inferCategory(itemId);
            if (categoryKey == null) continue;
            String baseName = resolveDisplayName(itemId);

            Nat20LootData data = pipeline.generate(
                    itemId, baseName, categoryKey,
                    effectiveMin, effectiveMax,
                    difficulty,
                    rng, ilvl);

            if (data == null) {
                LOGGER.atWarning().log("Pipeline returned null (itemId=%s ilvl=%d source=%s); skipping slot %d",
                        itemId, ilvl, pick.source(), slot);
                continue;
            }

            results.add(new GeneratedDrop(itemId, data));
            LOGGER.atInfo().log("Drop %d/%d: %s [%s] from %s (source=%s)",
                    slot, dropCount, data.getGeneratedName(), data.getRarity(), itemId, pick.source());
        }

        return results;
    }

    private String resolveDisplayName(String itemId) {
        String name = lootSystem.getLootEntryRegistry().getDisplayName(itemId);
        if (name != null) return name;
        name = lootSystem.getItemRegistry().resolveItemDisplayName(itemId);
        return name != null ? name : itemId;
    }

    @Nullable
    private ItemStack buildItemStack(String baseItemId, Nat20LootData data) {
        String stackItemId = data.getUniqueItemId() != null && !data.getUniqueItemId().isEmpty()
                ? data.getUniqueItemId()
                : baseItemId;
        if (stackItemId == null || stackItemId.isEmpty()) return null;
        try {
            return new ItemStack(stackItemId, 1).withMetadata(Nat20LootData.METADATA_KEY, data);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to build ItemStack for drop: itemId=%s", stackItemId);
            return null;
        }
    }
}
