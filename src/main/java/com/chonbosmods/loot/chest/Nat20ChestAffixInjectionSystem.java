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
 * Orchestrates Natural 20 affix-loot injection into native chests on first
 * secondary-use (right-click) interaction.
 *
 * <p>Thin event handler: delegates all decisions to injected collaborators.
 * On a qualifying chest open, marks the position as rolled BEFORE running the
 * chance gate so that every chest rolls exactly once regardless of whether
 * the roll wins, fails to pick, or throws. The registry is the source of
 * truth; the chance roll is a downstream filter.
 *
 * <p>See {@code docs/plans/2026-04-21-chest-affix-loot-injection.md} Task 6.
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
        String debugBlockId = event.getBlockType() != null ? event.getBlockType().getId() : "<null>";
        LOGGER.atInfo().log("UseBlockEvent.Pre: block=%s interaction=%s cancelled=%s",
                debugBlockId, event.getInteractionType(), event.isCancelled());

        if (event.isCancelled()) return;
        if (event.getInteractionType() != InteractionType.Use) return;
        if (event.getBlockType() == null) return;
        String blockTypeId = event.getBlockType().getId();
        if (blockTypeId == null || !config.isChestBlock(blockTypeId)) return;

        Vector3i pos = event.getTargetBlock();
        if (pos == null) return;
        if (registry.hasBeenRolled(pos.getX(), pos.getY(), pos.getZ())) return;

        registry.markRolled(pos.getX(), pos.getY(), pos.getZ());

        double dist = Math.hypot(pos.getX(), pos.getZ());
        int areaLevel = scalingConfig.areaLevelForDistance(dist);
        Random rng = ThreadLocalRandom.current();
        if (!roller.roll(rng)) return;

        Optional<Nat20LootData> loot = picker.pickLoot(areaLevel, rng);
        if (loot.isEmpty()) return;

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        Nat20LootData data = loot.get();
        ItemStack stack = buildItemStack(data);
        if (stack == null) return;

        boolean injected = Nat20ChestContainerWriter.injectIntoFirstEmptySlot(
                world, pos.getX(), pos.getY(), pos.getZ(), stack);
        if (injected) {
            // Cancel this first interaction so the chest UI never opens. On the
            // player's next Use press our handler short-circuits at the
            // hasBeenRolled() gate above, the native open flow runs, and the UI
            // renders from a settled container state with our item already in place.
            // Rationale: mutating the container during the open transition (Attempt 1
            // / 3) makes setState tear down the client UI mid-render. Deferring the
            // open entirely sidesteps that teardown window at the cost of one extra
            // F-press on first discovery. See handoff doc Theory 2b.
            event.setCancelled(true);
            LOGGER.atInfo().log("Chest inject at %d,%d,%d areaLevel=%d -> %s (cancelled first open; re-press Use to view)",
                    pos.getX(), pos.getY(), pos.getZ(), areaLevel, data.getGeneratedName());
        }
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
