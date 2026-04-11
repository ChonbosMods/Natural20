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
import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Attack Speed affix system: periodically scans online players' equipped weapons for
 * the attack_speed affix and applies/removes an AttackSpeed stat modifier accordingly.
 *
 * <p>Runs on a 250ms tick via a daemon ScheduledExecutorService, dispatching entity
 * work to the world thread via {@code world.execute()}.
 *
 * <p>Falls back to the StaticModifier approach since InteractionManager is not exposed
 * as an ECS component in the current SDK.
 */
public class Nat20AttackSpeedSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:attack_speed";
    private static final String MODIFIER_KEY = "nat20:attack_speed_bonus";
    private static final double SOFTCAP_K = 0.35;
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

    /**
     * Start the periodic tick executor. Called from Natural20.start().
     */
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat20-attack-speed");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Shut down the tick executor. Called from Natural20.shutdown().
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        activeShifts.clear();
    }

    /**
     * Periodic tick: dispatches to the world thread to scan weapons and apply modifiers.
     */
    private void tick() {
        if (trackedPlayers.isEmpty()) return;

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int attackSpeedIdx = EntityStatType.getAssetMap().getIndex("AttackSpeed");
            if (attackSpeedIdx == AssetMapWithIndexes.NOT_FOUND) return;

            for (UUID playerUuid : trackedPlayers) {
                try {
                    processPlayer(world, store, playerUuid, attackSpeedIdx);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error processing attack speed for player %s",
                            playerUuid.toString().substring(0, 8));
                }
            }
        });
    }

    private void processPlayer(World world, Store<EntityStore> store, UUID playerUuid, int attackSpeedIdx) {
        Ref<EntityStore> ref = world.getEntityRef(playerUuid);
        if (ref == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        // Compute attack speed bonus from equipped weapon
        float bonus = computeAttackSpeedBonus(store, ref);

        Float previousShift = activeShifts.get(playerUuid);

        if (bonus > 0) {
            // Only apply if changed (avoid redundant modifier churn)
            if (previousShift != null && Float.compare(previousShift, bonus) == 0) return;

            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) return;

            // Remove old modifier before applying new one
            statMap.removeModifier(attackSpeedIdx, MODIFIER_KEY);
            statMap.putModifier(attackSpeedIdx, MODIFIER_KEY,
                    new StaticModifier(Modifier.ModifierTarget.MAX,
                            StaticModifier.CalculationType.MULTIPLICATIVE, bonus));
            activeShifts.put(playerUuid, bonus);

            if (CombatDebugSystem.isEnabled(playerUuid)) {
                LOGGER.atInfo().log("[AttackSpeed] player=%s bonus=%.3f (applied as AttackSpeed MULTIPLICATIVE modifier)",
                        playerUuid.toString().substring(0, 8), bonus);
            }
        } else if (previousShift != null) {
            // Remove modifier when no longer applicable
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap != null) {
                statMap.removeModifier(attackSpeedIdx, MODIFIER_KEY);
            }
            activeShifts.remove(playerUuid);

            if (CombatDebugSystem.isEnabled(playerUuid)) {
                LOGGER.atInfo().log("[AttackSpeed] player=%s modifier removed (no attack_speed affix on weapon)",
                        playerUuid.toString().substring(0, 8));
            }
        }
    }

    /**
     * Scan the player's weapon in hand for the attack_speed affix and compute the
     * effective bonus value with DEX scaling and softcap.
     *
     * @return softcapped bonus value, or 0 if no attack_speed affix found
     */
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

            // Check stat requirement (DEX >= 12)
            PlayerStats playerStats = resolvePlayerStats(playerRef, store);
            if (def.statRequirement() != null && playerStats != null) {
                boolean requirementsMet = true;
                for (var req : def.statRequirement().entrySet()) {
                    if (playerStats.stats()[req.getKey().index()] < req.getValue()) {
                        requirementsMet = false;
                        break;
                    }
                }
                if (!requirementsMet) return 0f;
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

    /**
     * Resolve the player's D&D stats for affix scaling, or null if unavailable.
     */
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
