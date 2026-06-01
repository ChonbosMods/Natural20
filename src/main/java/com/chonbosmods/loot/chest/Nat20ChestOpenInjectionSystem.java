package com.chonbosmods.loot.chest;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootPipeline;
import com.chonbosmods.progression.MobScalingConfig;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Injects Nat20 affix loot into native worldgen loot chests the first time a player opens
 * them, minting on demand so that chests which are generated but never opened cost nothing.
 *
 * <p>Eligibility is decided at chunk generation by {@link Nat20ChestEligibilityStampSystem}
 * (worldgen chests carry an engine {@code droplist}); this handler only fires for positions
 * that were stamped and not yet looted, so player-placed and broken-and-replaced chests are
 * never touched.
 *
 * <p>Double-tap flow: on the first {@code Use} press of an eligible chest we mint + write the
 * item(s) into the container (direct component mutation, no {@code setState}) and cancel the
 * event so the window does not open this press. The eligibility is consumed immediately, so
 * the player's second press skips this handler and the native open reads a settled container
 * — the same ordering the engine's StashSystem relies on, which avoids the setState render
 * glitch.
 *
 * <p>Item level comes from area strength (distance), matching the rest of the loot system.
 */
public class Nat20ChestOpenInjectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestOpenInject");
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20ChestLootConfig config;
    private final Nat20ChestLootRoller roller;
    private final Nat20ChestLootPicker picker;
    private final MobScalingConfig scalingConfig;
    private final Nat20ChestEligibilityRegistry registry;

    public Nat20ChestOpenInjectionSystem(Nat20ChestLootConfig config,
                                         Nat20ChestLootRoller roller,
                                         Nat20ChestLootPicker picker,
                                         MobScalingConfig scalingConfig,
                                         Nat20ChestEligibilityRegistry registry) {
        super(UseBlockEvent.Pre.class);
        this.config = config;
        this.roller = roller;
        this.picker = picker;
        this.scalingConfig = scalingConfig;
        this.registry = registry;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> cb,
                       UseBlockEvent.Pre event) {
        if (event.isCancelled()) return;
        if (event.getInteractionType() != InteractionType.Use) return;

        Vector3i pos = event.getTargetBlock();
        if (pos == null) return;
        if (!registry.isEligible(pos.x(), pos.y(), pos.z())) return;

        // Consume eligibility up front so this chest rolls exactly once, even if the press is
        // double-fired; a failed chance just opens the chest normally below.
        registry.markLooted(pos.x(), pos.y(), pos.z());

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        Random rng = ThreadLocalRandom.current();
        if (!roller.rollPrimary(rng)) return;

        double dist = Math.hypot(pos.x(), pos.z());
        int areaLevel = scalingConfig.areaLevelForDistance(dist);

        boolean anyInjected = injectOne(world, pos, areaLevel, rng, null, "primary");
        if (!anyInjected) return;

        if (roller.rollSecondary(rng)) {
            Integer secondaryMax = rng.nextDouble() < config.getSecondaryLowRarityBias()
                    ? 2
                    : null;
            injectOne(world, pos, areaLevel, rng, secondaryMax, "secondary");
        }

        // Suppress this open; the next press reads the settled container.
        event.setCancelled(true);
    }

    private boolean injectOne(World world, Vector3i pos, int areaLevel, Random rng,
                              Integer maxRarityTierOverride, String tag) {
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

        boolean injected = Nat20ChestContainerWriter.injectIntoFirstEmptySlot(
                world, pos.x(), pos.y(), pos.z(), stack);
        if (injected) {
            LOGGER.atInfo().log("Chest open-inject (%s) at %d,%d,%d areaLevel=%d -> %s [%s]",
                    tag, pos.x(), pos.y(), pos.z(), areaLevel, data.getGeneratedName(), data.getRarity());
        }
        return injected;
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
