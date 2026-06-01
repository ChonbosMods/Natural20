package com.chonbosmods.loot.chest;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootPipeline;
import com.chonbosmods.progression.MobScalingConfig;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Injects Nat20 affix loot into native worldgen loot chests when their block
 * component first spawns (chunk generation), reusing the engine's own loot-chest
 * primitive instead of intercepting the player open.
 *
 * <p>Hytale's {@code StashPlugin.StashSystem} lazily populates loot chests: a
 * worldgen chest carries a {@code droplist} (loot-table id) on its
 * {@link ItemContainerBlock}; on first spawn the engine rolls the droplist, fills
 * the container, and (by default) clears the droplist. Player-placed chests,
 * broken-and-replaced chests, and already-looted chests have a {@code null}
 * droplist. We piggyback on the same lifecycle:
 *
 * <ul>
 *   <li><b>Idempotency:</b> only {@link AddReason#SPAWN} (a newly generated chest),
 *       never {@link AddReason#LOAD} (a disk reload). A chest is injected exactly
 *       once, ever, regardless of the {@code clearContainerDropList} config.</li>
 *   <li><b>Eligibility:</b> {@code getDroplist() != null}. This is the authoritative,
 *       engine-persisted "fresh worldgen loot chest" flag. It dies with the block, so
 *       a looted chest that is broken and re-placed is inert (it is player-placed and
 *       has no droplist). Quest chests, placed via {@code QuestChestPlacer} with an
 *       explicit container and no droplist, are excluded automatically.</li>
 *   <li><b>Ordering:</b> {@link RootDependency#firstSet()} runs us before
 *       {@code StashSystem}, so we see the droplist while it is still set and inject
 *       into the empty container. Stash then fills the remaining slots around our
 *       item ({@code addItemStackToSlot} respects occupied slots).</li>
 * </ul>
 *
 * <p>Because injection happens at spawn with no viewer and mutates the component
 * directly (no {@code setState} rebroadcast), it avoids the open-time render glitch
 * and the duplicate/stack-of-2 races of the retired {@code UseBlockEvent} path.
 *
 * <p>Item level comes solely from area strength (distance from origin), never from a
 * player level: there is no player in scope at chunk generation.
 */
public class Nat20ChestLootSystem extends RefSystem<ChunkStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestLoot");

    private final ComponentType<ChunkStore, ItemContainerBlock> itemContainerType;
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoType;
    private final ComponentType<ChunkStore, WorldChunk> worldChunkType;
    private final Query<ChunkStore> query;

    private final Nat20ChestLootConfig config;
    private final Nat20ChestLootRoller roller;
    private final Nat20ChestLootPicker picker;
    private final MobScalingConfig scalingConfig;

    public Nat20ChestLootSystem(Nat20ChestLootConfig config, Nat20ChestLootRoller roller,
                                Nat20ChestLootPicker picker, MobScalingConfig scalingConfig) {
        this.itemContainerType = ItemContainerBlock.getComponentType();
        this.blockStateInfoType = BlockModule.BlockStateInfo.getComponentType();
        this.worldChunkType = WorldChunk.getComponentType();
        this.query = Query.and(this.itemContainerType, this.blockStateInfoType);
        this.config = config;
        this.roller = roller;
        this.picker = picker;
        this.scalingConfig = scalingConfig;
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        // Run before StashSystem (and all other ChunkStore systems) so the droplist is
        // still present and the container is still empty when we inject.
        return RootDependency.firstSet();
    }

    @Override
    public void onEntityAdded(Ref<ChunkStore> ref, AddReason reason,
                              Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        if (reason != AddReason.SPAWN) return;

        ItemContainerBlock block = store.getComponent(ref, itemContainerType);
        if (block == null) return;
        if (block.getDroplist() == null) return;

        SimpleItemContainer container = block.getItemContainer();
        if (container == null) return;

        BlockModule.BlockStateInfo info = store.getComponent(ref, blockStateInfoType);
        if (info == null) return;
        WorldChunk chunk = store.getComponent(info.getChunkRef(), worldChunkType);
        if (chunk == null) return;

        int idx = info.getIndex();
        int wx = ChunkUtil.worldCoordFromLocalCoord(chunk.getX(), ChunkUtil.xFromIndex(idx));
        int wy = ChunkUtil.yFromIndex(idx);
        int wz = ChunkUtil.worldCoordFromLocalCoord(chunk.getZ(), ChunkUtil.zFromIndex(idx));

        Random rng = ThreadLocalRandom.current();
        if (!roller.rollPrimary(rng)) return;

        double dist = Math.hypot(wx, wz);
        int areaLevel = scalingConfig.areaLevelForDistance(dist);

        boolean anyInjected = injectOne(container, areaLevel, rng, null, "primary", wx, wy, wz);
        if (!anyInjected) return;

        if (roller.rollSecondary(rng)) {
            // Soft-bias the bonus toward low rarity: getSecondaryLowRarityBias() of the
            // time cap at Uncommon (tier 2); otherwise use the full ilvl gate.
            Integer secondaryMax = rng.nextDouble() < config.getSecondaryLowRarityBias()
                    ? 2
                    : null;
            injectOne(container, areaLevel, rng, secondaryMax, "secondary", wx, wy, wz);
        }

        // Persist the mutated container with the freshly generated chunk.
        info.markNeedsSaving();
    }

    @Override
    public void onEntityRemove(Ref<ChunkStore> ref, RemoveReason reason,
                               Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        // No teardown work: nat20 loot lives in the container like any other item.
    }

    private boolean injectOne(SimpleItemContainer container, int areaLevel, Random rng,
                              Integer maxRarityTierOverride, String tag, int x, int y, int z) {
        Optional<Nat20LootData> loot;
        if (maxRarityTierOverride == null) {
            loot = picker.pickLoot(areaLevel, rng);
        } else {
            int[] gate = Nat20LootPipeline.rarityGateForIlvl(areaLevel);
            loot = picker.pickLoot(areaLevel, gate[0], maxRarityTierOverride, rng);
        }
        if (loot.isEmpty()) return false;

        Nat20LootData data = loot.get();
        ItemStack stack = buildItemStack(data);
        if (stack == null) return false;

        short slot = firstEmptySlot(container);
        if (slot < 0) return false;

        container.setItemStackForSlot(slot, stack);
        LOGGER.atInfo().log("Chest loot (%s) at %d,%d,%d areaLevel=%d -> %s [%s] slot %d",
                tag, x, y, z, areaLevel, data.getGeneratedName(), data.getRarity(), slot);
        return true;
    }

    private static short firstEmptySlot(SimpleItemContainer container) {
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            if (ItemStack.isEmpty(container.getItemStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static ItemStack buildItemStack(Nat20LootData data) {
        String stackItemId = data.getUniqueItemId();
        if (stackItemId == null || stackItemId.isEmpty()) {
            stackItemId = data.getVariantItemId();
        }
        if (stackItemId == null || stackItemId.isEmpty()) {
            LOGGER.atWarning().log("Chest loot %s has no uniqueItemId or variantItemId; cannot build ItemStack",
                    data.getGeneratedName());
            return null;
        }
        try {
            // Resolve the base item id via the registry and stamp its maxDurability onto the
            // stack, sidestepping the Nat20ItemRegistry async-loadAssets race. See
            // AffixRewardRoller#rollFor for the full explanation. Quantity is always 1:
            // minted gear is non-stackable.
            String baseItemId = Natural20.getInstance().getLootSystem()
                    .getItemRegistry().getBaseItemId(data.getUniqueItemId());
            Item baseItem = baseItemId != null ? Item.getAssetMap().getAsset(baseItemId) : null;
            double baseMax = baseItem != null ? baseItem.getMaxDurability() : 0.0;
            return new ItemStack(stackItemId, 1)
                    .withRestoredDurability(baseMax)
                    .withMetadata(Nat20LootData.METADATA_KEY, data);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to build chest ItemStack for itemId=%s", stackItemId);
            return null;
        }
    }
}
