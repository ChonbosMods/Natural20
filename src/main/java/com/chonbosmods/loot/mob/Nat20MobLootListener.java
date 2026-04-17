package com.chonbosmods.loot.mob;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootPipeline;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.progression.DifficultyTier;
import com.chonbosmods.progression.Nat20MobLevel;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
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
 * via {@link DropItemEvent.Drop} fired through the CommandBuffer.
 */
public class Nat20MobLootListener {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
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
        if (dropCount <= 0) return List.of();

        int ilvl = level.getAreaLevel();
        List<String> eligibleItemIds = resolveEligibleGearItems(mobRef, store, ilvl);
        if (eligibleItemIds.isEmpty()) {
            LOGGER.atFine().log("No gear-eligible items in mob %s drop list at ilvl=%d; skipping Nat20 drops",
                    mobRef, ilvl);
            return List.of();
        }

        List<Nat20LootData> drops = generateDrops(eligibleItemIds, dropCount, ilvl);
        if (drops.isEmpty()) return drops;

        for (Nat20LootData data : drops) {
            ItemStack stack = buildItemStack(data);
            if (stack == null) continue;
            commandBuffer.invoke(mobRef, new DropItemEvent.Drop(stack, DROP_THROW_SPEED));
        }

        LOGGER.atInfo().log("Mob %s death drops: count=%d tier=%s ilvl=%d",
                mobRef, drops.size(), difficulty, ilvl);
        return drops;
    }

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
        if (dropListId == null || dropListId.isEmpty()) return List.of();

        ItemDropList dropList = ItemDropList.getAssetMap().getAsset(dropListId);
        if (dropList == null || dropList.getContainer() == null) return List.of();

        List<ItemDrop> allDrops = dropList.getContainer().getAllDrops(new ArrayList<>());
        List<String> eligible = new ArrayList<>();
        for (ItemDrop drop : allDrops) {
            String itemId = drop.getItemId();
            if (!Nat20ItemTierResolver.isGearItem(itemId)) continue;
            if (!Nat20ItemTierResolver.allowsIlvl(itemId, ilvl)) continue;
            eligible.add(itemId);
        }
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
    private List<Nat20LootData> generateDrops(List<String> eligibleItemIds, int dropCount, int ilvl) {
        Nat20LootPipeline pipeline = lootSystem.getPipeline();
        Random random = ThreadLocalRandom.current();
        List<Nat20LootData> results = new ArrayList<>(dropCount);

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
                results.add(data);
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

    /** Build an ItemStack with Nat20LootData metadata attached. */
    @Nullable
    private ItemStack buildItemStack(Nat20LootData data) {
        String variant = data.getVariantItemId();
        if (variant == null || variant.isEmpty()) return null;
        try {
            return new ItemStack(variant, 1).withMetadata(Nat20LootData.METADATA_KEY, data);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to build ItemStack for drop: itemId=%s", variant);
            return null;
        }
    }
}
