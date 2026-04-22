package com.chonbosmods.loot.chest;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.progression.MobScalingConfig;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Injects Nat20 affix loot into native chests on first Use (F-key) interaction.
 *
 * <p>On a qualifying chest open: marks the position rolled BEFORE any probability
 * check so every chest rolls exactly once. Rolls the primary chance; on hit,
 * generates a gear item at the area's ilvl using the pipeline's default rarity
 * gate. If the primary succeeds, rolls the conditional secondary chance; on hit,
 * generates a second item with its max rarity clamped (default Uncommon) so the
 * bonus skews toward Common/Uncommon. Both items are injected into the next
 * available empty slot via {@link Nat20ChestContainerWriter}.
 *
 * <p>After injection, cancels the event so the chest UI does not open on this
 * press. The player's next Use press short-circuits at the registry gate and the
 * native open flow renders the chest from a settled container state — sidestepping
 * the setState mid-render teardown that glitches the UI (handoff Theory 2b).
 *
 * <p>See {@code docs/plans/2026-04-21-chest-affix-loot-injection.md} Task 6 and
 * {@code docs/plans/2026-04-22-chest-loot-injection-handoff.md}.
 */
public class Nat20ChestAffixInjectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ChestAffixInjection");
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20ChestLootConfig config;
    private final Nat20ChestLootRoller roller;
    private final Nat20ChestRollRegistry registry;
    private final MobScalingConfig scalingConfig;
    private final Nat20ChestLootPicker picker;

    public Nat20ChestAffixInjectionSystem(
            Nat20ChestLootConfig config,
            Nat20ChestLootRoller roller,
            Nat20ChestRollRegistry registry,
            MobScalingConfig scalingConfig,
            Nat20ChestLootPicker picker) {
        super(UseBlockEvent.Pre.class);
        this.config = config;
        this.roller = roller;
        this.registry = registry;
        this.scalingConfig = scalingConfig;
        this.picker = picker;
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
        if (event.getBlockType() == null) return;
        String blockTypeId = event.getBlockType().getId();
        if (blockTypeId == null || !config.isChestBlock(blockTypeId)) return;

        Vector3i pos = event.getTargetBlock();
        if (pos == null) return;
        if (registry.hasBeenRolled(pos.getX(), pos.getY(), pos.getZ())) return;

        registry.markRolled(pos.getX(), pos.getY(), pos.getZ());

        Random rng = ThreadLocalRandom.current();
        if (!roller.rollPrimary(rng)) return;

        double dist = Math.hypot(pos.getX(), pos.getZ());
        int areaLevel = scalingConfig.areaLevelForDistance(dist);

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        boolean anyInjected = injectOne(world, pos, areaLevel, rng, null, "primary");
        if (!anyInjected) return;

        if (roller.rollSecondary(rng)) {
            injectOne(world, pos, areaLevel, rng, config.getSecondaryMaxRarityTier(), "secondary");
        }

        // Suppress this open — the player's next Use press reads a settled state.
        event.setCancelled(true);
    }

    /**
     * Generate and inject one item at the chest position.
     *
     * @param maxRarityTierOverride null for the default ilvl gate, or a tier cap to
     *                              bias toward lower rarities (secondary roll).
     * @return true if an ItemStack was placed into an empty slot.
     */
    private boolean injectOne(World world, Vector3i pos, int areaLevel, Random rng,
                              Integer maxRarityTierOverride, String tag) {
        Optional<Nat20LootData> loot;
        if (maxRarityTierOverride == null) {
            loot = picker.pickLoot(areaLevel, rng);
        } else {
            int[] gate = com.chonbosmods.loot.Nat20LootPipeline.rarityGateForIlvl(areaLevel);
            loot = picker.pickLoot(areaLevel, gate[0], maxRarityTierOverride, rng);
        }
        if (loot.isEmpty()) return false;

        Nat20LootData data = loot.get();
        ItemStack stack = buildItemStack(data);
        if (stack == null) return false;

        boolean injected = Nat20ChestContainerWriter.injectIntoFirstEmptySlot(
                world, pos.getX(), pos.getY(), pos.getZ(), stack);
        if (injected) {
            LOGGER.atInfo().log("Chest inject (%s) at %d,%d,%d areaLevel=%d -> %s [%s]",
                    tag, pos.getX(), pos.getY(), pos.getZ(), areaLevel,
                    data.getGeneratedName(), data.getRarity());
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
            return new ItemStack(stackItemId, 1).withMetadata(Nat20LootData.METADATA_KEY, data);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to build chest ItemStack for itemId=%s", stackItemId);
            return null;
        }
    }
}
