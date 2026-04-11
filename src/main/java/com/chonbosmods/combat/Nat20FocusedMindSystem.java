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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Focused Mind affix system: boosts mana regeneration for idle players.
 * Ticks every 1 second. When a player is standing still and their mana
 * increased since the last tick (natural regen), the increase is multiplied
 * by the affix's effective bonus.
 */
public class Nat20FocusedMindSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:focused_mind";
    private static final double IDLE_THRESHOLD_SQ = 0.01;

    private final Nat20LootSystem lootSystem;
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;

    public Nat20FocusedMindSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    public void addPlayer(UUID uuid) {
        trackedPlayers.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        trackedPlayers.remove(uuid);
        playerStates.remove(uuid);
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat20-focused-mind");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        trackedPlayers.clear();
        playerStates.clear();
    }

    private void tick() {
        if (trackedPlayers.isEmpty()) return;

        World world = Natural20.getInstance().getDefaultWorld();
        if (world == null) return;

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int manaIdx = EntityStatType.getAssetMap().getIndex("Mana");
            if (manaIdx < 0) return;

            for (UUID playerUuid : trackedPlayers) {
                try {
                    tickPlayer(world, store, playerUuid, manaIdx);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error ticking focused mind for %s", playerUuid);
                }
            }
        });
    }

    private void tickPlayer(World world, Store<EntityStore> store, UUID playerUuid, int manaIdx) {
        Ref<EntityStore> entityRef = world.getEntityRef(playerUuid);
        if (entityRef == null) return;

        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (transform == null) return;

        double x = transform.getPosition().getX();
        double y = transform.getPosition().getY();
        double z = transform.getPosition().getZ();

        EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        float currentMana = statMap.get(manaIdx).get();

        PlayerState prev = playerStates.get(playerUuid);
        if (prev == null) {
            // First tick: store baseline state
            playerStates.put(playerUuid, new PlayerState(x, y, z, currentMana));
            return;
        }

        // Check idle: position barely changed since last tick
        double dx = x - prev.lastX;
        double dy = y - prev.lastY;
        double dz = z - prev.lastZ;
        boolean idle = (dx * dx + dy * dy + dz * dz) < IDLE_THRESHOLD_SQ;

        // Check if mana increased (natural regen)
        float manaDelta = currentMana - prev.lastMana;
        boolean manaIncreased = manaDelta > 0;

        if (idle && manaIncreased) {
            // Scan equipped items for focused_mind affix
            double totalBonus = computeTotalBonus(store, entityRef);

            if (totalBonus > 0) {
                // Apply softcap (k=2.0)
                double effectiveBonus = Nat20Softcap.softcap(totalBonus, 2.0);

                // Boost = natural regen delta * bonus multiplier
                float boost = (float) (manaDelta * effectiveBonus);

                // Clamp to max mana
                float maxMana = statMap.get(manaIdx).getMax();
                float newMana = Math.min(currentMana + boost, maxMana);
                boost = newMana - currentMana;

                if (boost > 0) {
                    statMap.addStatValue(manaIdx, boost);

                    if (CombatDebugSystem.isEnabled(playerUuid)) {
                        LOGGER.atInfo().log("[FocusedMind] player=%s idle=true delta=%.2f raw=%.3f effective=%.3f boost=%.2f mana=%.0f->%.0f",
                                playerUuid.toString().substring(0, 8),
                                manaDelta, totalBonus, effectiveBonus, boost,
                                currentMana, currentMana + boost);
                    }
                }
            }
        }

        // Update stored state
        prev.lastX = x;
        prev.lastY = y;
        prev.lastZ = z;
        prev.lastMana = idle && manaIncreased ? statMap.get(manaIdx).get() : currentMana;
    }

    /**
     * Scan weapon in hand and all armor slots for the focused_mind affix,
     * summing up effective values from all equipped pieces.
     */
    private double computeTotalBonus(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        PlayerStats playerStats = resolvePlayerStats(entityRef, store);
        double total = 0.0;

        // Weapon in hand
        ItemStack weapon = InventoryComponent.getItemInHand(store, entityRef);
        if (weapon != null && !weapon.isEmpty()) {
            total += scanItemForFocusedMind(weapon, affixRegistry, playerStats);
        }

        // All armor slots
        @SuppressWarnings("unchecked")
        CombinedItemContainer armorContainer = InventoryComponent.getCombined(
                store, entityRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armorContainer != null) {
            short armorCapacity = armorContainer.getCapacity();
            for (short slot = 0; slot < armorCapacity; slot++) {
                ItemStack armorPiece = armorContainer.getItemStack(slot);
                if (armorPiece == null || armorPiece.isEmpty()) continue;
                total += scanItemForFocusedMind(armorPiece, affixRegistry, playerStats);
            }
        }

        return total;
    }

    /**
     * Scan a single item for the focused_mind affix and compute its effective value.
     */
    private double scanItemForFocusedMind(ItemStack item, Nat20AffixRegistry affixRegistry,
                                           PlayerStats playerStats) {
        Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return 0.0;

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return 0.0;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return 0.0;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;

            if (playerStats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = playerStats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }

            return effectiveValue;
        }

        return 0.0;
    }

    /**
     * Resolve the player's D&D stats for affix scaling, or null if unavailable.
     */
    private PlayerStats resolvePlayerStats(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            return playerData != null ? PlayerStats.from(playerData) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Mutable per-player state for tracking position and mana between ticks.
     */
    private static class PlayerState {
        double lastX, lastY, lastZ;
        float lastMana;

        PlayerState(double x, double y, double z, float mana) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastMana = mana;
        }
    }
}
