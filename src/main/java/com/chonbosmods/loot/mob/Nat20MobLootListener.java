package com.chonbosmods.loot.mob;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootPipeline;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.Nat20MobLevel;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates enhanced loot drops when a Nat20-tiered mob dies.
 *
 * Drop count: tier table keyed by {@link DifficultyTier}.
 * Base item pool: the mob's native {@link ItemDropList}, filtered to gear items
 * whose material tier matches the mob's ilvl (see {@link Nat20ItemTierResolver}).
 * Each drop slot picks uniformly from the filtered pool, runs through the
 * Nat20 loot pipeline (rarity + affixes + name), and spawns as a world-entity
 * via {@link ItemUtils#throwItem} on the CommandBuffer.
 */
public class Nat20MobLootListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobLootListener");
    private static final float DROP_THROW_SPEED = 0.0f;

    private final Nat20LootSystem lootSystem;

    public Nat20MobLootListener(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    /**
     * Generate and spawn drops for a mob that just took lethal damage.
     * @return the list of generated loot data (for logging/telemetry).
     */
    public List<Nat20LootData> onMobDeath(Ref<EntityStore> mobRef, Store<EntityStore> store,
                                           CommandBuffer<EntityStore> commandBuffer) {
        Nat20MobLevel level = store.getComponent(mobRef, Natural20.getMobLevelType());
        if (level == null) return List.of();

        DifficultyTier difficulty = level.getDifficultyTier();
        int dropCount = dropCountFor(difficulty);
        LOGGER.atInfo().log("onMobDeath called for mob %s: tier=%s ilvl=%d dropCount=%d",
                mobRef, difficulty, level.getAreaLevel(), dropCount);
        if (dropCount <= 0) return List.of();

        int ilvl = level.getAreaLevel();
        List<String> eligibleItemIds = resolveEligibleGearItems(mobRef, store, ilvl);
        if (eligibleItemIds.isEmpty()) {
            LOGGER.atInfo().log("No gear-eligible items in mob %s drop list at ilvl=%d; skipping Nat20 drops",
                    mobRef, ilvl);
            return List.of();
        }

        List<GeneratedDrop> drops = generateDrops(eligibleItemIds, dropCount, ilvl);
        if (drops.isEmpty()) return List.of();

        for (GeneratedDrop drop : drops) {
            ItemStack stack = buildItemStack(drop.baseItemId(), drop.data());
            if (stack == null) continue;
            ItemUtils.throwItem(mobRef, stack, DROP_THROW_SPEED, commandBuffer);
        }

        LOGGER.atInfo().log("Mob %s death drops: count=%d tier=%s ilvl=%d",
                mobRef, drops.size(), difficulty, ilvl);
        List<Nat20LootData> result = new ArrayList<>(drops.size());
        for (GeneratedDrop drop : drops) result.add(drop.data());
        return result;
    }

    private record GeneratedDrop(String baseItemId, Nat20LootData data) {}

    /**
     * Look up the mob's NPC role, resolve its ItemDropList, and return the list of
     * gear-item IDs whose material tier matches the given ilvl.
     */
    private List<String> resolveEligibleGearItems(Ref<EntityStore> mobRef,
                                                   Store<EntityStore> store, int ilvl) {
        NPCEntity npc = store.getComponent(mobRef, NPCEntity.getComponentType());
        if (npc == null) return List.of();
        Role role = npc.getRole();
        if (role == null) return List.of();

        String dropListId = role.getDropListId();
        if (dropListId == null || dropListId.isEmpty()) {
            LOGGER.atInfo().log("Mob %s has no drop list id (role=%s)", mobRef, role.getClass().getSimpleName());
            return List.of();
        }

        ItemDropList dropList = ItemDropList.getAssetMap().getAsset(dropListId);
        if (dropList == null || dropList.getContainer() == null) {
            LOGGER.atInfo().log("Mob %s drop list %s missing or empty", mobRef, dropListId);
            return List.of();
        }

        List<ItemDrop> allDrops = dropList.getContainer().getAllDrops(new ArrayList<>());
        List<String> eligible = new ArrayList<>();
        List<String> allItemIds = new ArrayList<>(allDrops.size());
        for (ItemDrop drop : allDrops) {
            String itemId = drop.getItemId();
            allItemIds.add(itemId);
            if (!Nat20ItemTierResolver.isGearItem(itemId)) continue;
            if (!Nat20ItemTierResolver.allowsIlvl(itemId, ilvl)) continue;
            eligible.add(itemId);
        }
        LOGGER.atInfo().log("Mob %s dropList=%s total=%d eligible=%d (all=%s)",
                mobRef, dropListId, allItemIds.size(), eligible.size(), allItemIds);
        return eligible;
    }

    /** Drop count per DifficultyTier. null (native Hytale mobs) drops nothing extra. */
    private int dropCountFor(@Nullable DifficultyTier difficulty) {
        if (difficulty == null) return 0;
        return switch (difficulty) {
            case UNCOMMON -> 1;
            case RARE -> 2;
            case EPIC -> 3;
            case LEGENDARY -> 4;
        };
    }

    /**
     * Pick random items from the eligible pool and generate Nat20 loot data for each.
     */
    private List<GeneratedDrop> generateDrops(List<String> eligibleItemIds, int dropCount, int ilvl) {
        Nat20LootPipeline pipeline = lootSystem.getPipeline();
        Random random = ThreadLocalRandom.current();
        List<GeneratedDrop> results = new ArrayList<>(dropCount);

        int[] gate = Nat20LootPipeline.rarityGateForIlvl(ilvl);
        int effectiveMin = gate[0];
        int effectiveMax = gate[1];

        for (int i = 0; i < dropCount; i++) {
            String itemId = eligibleItemIds.get(random.nextInt(eligibleItemIds.size()));
            String categoryKey = Nat20ItemTierResolver.inferCategory(itemId);
            if (categoryKey == null) continue;
            String baseName = resolveDisplayName(itemId);

            Nat20LootData data = pipeline.generate(
                    itemId, baseName, categoryKey,
                    effectiveMin, effectiveMax,
                    random, ilvl);

            if (data != null) {
                results.add(new GeneratedDrop(itemId, data));
                LOGGER.atInfo().log("Generated mob drop %d/%d: %s [%s] from %s",
                        i + 1, dropCount, data.getGeneratedName(), data.getRarity(), itemId);
            } else {
                LOGGER.atWarning().log("Pipeline returned null for mob drop %d/%d (itemId=%s ilvl=%d)",
                        i + 1, dropCount, itemId, ilvl);
            }
        }

        return results;
    }

    private String resolveDisplayName(String itemId) {
        String name = lootSystem.getLootEntryRegistry().getDisplayName(itemId);
        if (name != null) return name;
        name = lootSystem.getItemRegistry().resolveItemDisplayName(itemId);
        return name != null ? name : itemId;
    }

    /**
     * Build an ItemStack with Nat20LootData metadata attached. Prefers the dynamically
     * registered {@code uniqueItemId} (set by {@link Nat20LootPipeline} via
     * {@code Nat20ItemRegistry.registerItem}) and falls back to the base Hytale item id
     * if registration didn't happen, matching {@code AffixRewardRoller#buildItemStack}.
     */
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
