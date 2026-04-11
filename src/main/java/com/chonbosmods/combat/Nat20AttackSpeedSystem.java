package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Attack Speed affix system: periodically scans online players' equipped weapons for
 * the attack_speed affix and applies a time shift to their InteractionManager chains.
 *
 * <p>Uses the InteractionManager ECS component (obtained via InteractionModule) to
 * call {@code setGlobalTimeShift()} and {@code chain.setTimeShift()} on active chains.
 * Positive time shift = faster interactions.
 *
 * <p>Runs on a 250ms tick via a daemon ScheduledExecutorService, dispatching entity
 * work to the world thread via {@code world.execute()}.
 */
public class Nat20AttackSpeedSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:attack_speed";
    // TODO: restore to 0.35 after testing
    private static final double SOFTCAP_K = 100.0;
    private static final long TICK_INTERVAL_MS = 250L;

    private final Nat20LootSystem lootSystem;
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Float> activeShifts = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;

    public Nat20AttackSpeedSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    public void addPlayer(UUID uuid) {
        trackedPlayers.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        trackedPlayers.remove(uuid);
        activeShifts.remove(uuid);
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat20-attack-speed");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        activeShifts.clear();
    }

    private void tick() {
        if (trackedPlayers.isEmpty()) return;

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            ComponentType<EntityStore, InteractionManager> imType =
                    InteractionModule.get().getInteractionManagerComponent();

            for (UUID playerUuid : trackedPlayers) {
                try {
                    processPlayer(world, store, playerUuid, imType);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error processing attack speed for player %s",
                            playerUuid.toString().substring(0, 8));
                }
            }
        });
    }

    private void processPlayer(World world, Store<EntityStore> store, UUID playerUuid,
                                ComponentType<EntityStore, InteractionManager> imType) {
        Ref<EntityStore> ref = world.getEntityRef(playerUuid);
        if (ref == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        float bonus = computeAttackSpeedBonus(store, ref);
        Float previousShift = activeShifts.get(playerUuid);

        if (bonus > 0) {
            if (previousShift != null && Float.compare(previousShift, bonus) == 0) return;

            InteractionManager im = store.getComponent(ref, imType);
            if (im == null) return;

            // Apply global time shift to all interaction types (persists between swings)
            for (InteractionType type : EnumSet.allOf(InteractionType.class)) {
                im.setGlobalTimeShift(type, bonus);
            }

            activeShifts.put(playerUuid, bonus);

            if (CombatDebugSystem.isEnabled(playerUuid)) {
                LOGGER.atInfo().log("[AttackSpeed] player=%s globalTimeShift=%.3f",
                        playerUuid.toString().substring(0, 8), bonus);
            }
        } else if (previousShift != null) {
            // Reset time shift when weapon unequipped
            InteractionManager im = store.getComponent(ref, imType);
            if (im != null) {
                for (InteractionType type : EnumSet.allOf(InteractionType.class)) {
                    im.setGlobalTimeShift(type, 0.0f);
                }
            }
            activeShifts.remove(playerUuid);

            if (CombatDebugSystem.isEnabled(playerUuid)) {
                LOGGER.atInfo().log("[AttackSpeed] player=%s RESET",
                        playerUuid.toString().substring(0, 8));
            }
        }
    }

    private float computeAttackSpeedBonus(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        ItemStack weapon = InventoryComponent.getItemInHand(store, playerRef);
        if (weapon == null || weapon.isEmpty()) return 0f;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return 0f;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return 0f;

            PlayerStats playerStats = resolvePlayerStats(playerRef, store);
            if (def.statRequirement() != null && playerStats != null) {
                for (var req : def.statRequirement().entrySet()) {
                    if (playerStats.stats()[req.getKey().index()] < req.getValue()) {
                        return 0f;
                    }
                }
            }

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return 0f;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;

            if (playerStats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = playerStats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }

            return (float) Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);
        }

        return 0f;
    }

    @Nullable
    private PlayerStats resolvePlayerStats(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            return playerData != null ? PlayerStats.from(playerData) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
