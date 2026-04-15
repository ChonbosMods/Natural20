package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20AffixScaling;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Focused Mind affix system: ECS ticking system that boosts mana regeneration
 * for idle players. Runs every game tick so the boost is smooth and matches
 * native regen cadence (no visible "jumps").
 *
 * <p>Detects idle state by comparing position between ticks. When a player is
 * standing still and their mana increased since last tick (natural regen),
 * multiplies the increase by the affix bonus.
 */
public class Nat20FocusedMindSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:focused_mind";
    private static final double IDLE_THRESHOLD_SQ = 0.01;
    private static final double SOFTCAP_K = 2.0;

    /** Log at most once per second (20 ticks) to avoid spamming. */
    private static final int LOG_INTERVAL_TICKS = 20;

    private final Nat20LootSystem lootSystem;
    private final Query<EntityStore> query;
    private final ConcurrentHashMap<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    public Nat20FocusedMindSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
        this.query = Query.and(new Query[]{
                Query.any(), UUIDComponent.getComponentType(), Player.getComponentType()
        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (dt <= 0.0f) return;

        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID playerUuid = uuidComp.getUuid();

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        double x = transform.getPosition().getX();
        double y = transform.getPosition().getY();
        double z = transform.getPosition().getZ();

        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int manaIdx = EntityStatType.getAssetMap().getIndex("Mana");
        if (manaIdx < 0) return;

        float currentMana = statMap.get(manaIdx).get();

        PlayerState prev = playerStates.get(playerUuid);
        if (prev == null) {
            playerStates.put(playerUuid, new PlayerState(x, y, z, currentMana));
            return;
        }

        // Check idle
        double dx = x - prev.lastX;
        double dy = y - prev.lastY;
        double dz = z - prev.lastZ;
        boolean idle = (dx * dx + dy * dy + dz * dz) < IDLE_THRESHOLD_SQ;

        // Check if mana increased (natural regen tick)
        float manaDelta = currentMana - prev.lastMana;

        if (idle && manaDelta > 0) {
            double totalBonus = computeTotalBonus(store, ref);

            if (totalBonus > 0) {
                double effectiveBonus = Nat20Softcap.softcap(totalBonus, SOFTCAP_K);
                float boost = (float) (manaDelta * effectiveBonus);

                // Clamp to max
                float maxMana = statMap.get(manaIdx).getMax();
                float newMana = Math.min(currentMana + boost, maxMana);
                boost = newMana - currentMana;

                if (boost > 0) {
                    statMap.addStatValue(manaIdx, boost);

                    // Log once per second, not every tick
                    prev.ticksSinceLog++;
                    if (prev.ticksSinceLog >= LOG_INTERVAL_TICKS && CombatDebugSystem.isEnabled(playerUuid)) {
                        LOGGER.atInfo().log("[FocusedMind] player=%s idle bonus=%.0f%% mana=%.0f/%.0f",
                                playerUuid.toString().substring(0, 8),
                                effectiveBonus * 100.0,
                                statMap.get(manaIdx).get(), maxMana);
                        prev.ticksSinceLog = 0;
                    }
                }

                prev.lastMana = statMap.get(manaIdx).get();
            } else {
                prev.lastMana = currentMana;
            }
        } else {
            prev.lastMana = currentMana;
            prev.ticksSinceLog = 0;
        }

        prev.lastX = x;
        prev.lastY = y;
        prev.lastZ = z;
    }

    private double computeTotalBonus(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        PlayerStats playerStats = resolvePlayerStats(entityRef, store);
        double total = 0.0;

        ItemStack weapon = InventoryComponent.getItemInHand(store, entityRef);
        if (weapon != null && !weapon.isEmpty()) {
            total += scanItemForAffix(weapon, affixRegistry, playerStats);
        }

        @SuppressWarnings("unchecked")
        CombinedItemContainer armor = InventoryComponent.getCombined(
                store, entityRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armor != null) {
            for (short slot = 0; slot < armor.getCapacity(); slot++) {
                ItemStack piece = armor.getItemStack(slot);
                if (piece == null || piece.isEmpty()) continue;
                total += scanItemForAffix(piece, affixRegistry, playerStats);
            }
        }

        return total;
    }

    private double scanItemForAffix(ItemStack item, Nat20AffixRegistry affixRegistry,
                                     PlayerStats playerStats) {
        Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return 0.0;

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return 0.0;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return 0.0;

            double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, lootSystem.getRarityRegistry());
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

    @Nullable
    private PlayerStats resolvePlayerStats(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            return playerData != null ? PlayerStats.from(playerData) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Clean up state on disconnect. */
    public void removePlayer(UUID uuid) {
        playerStates.remove(uuid);
    }

    private static class PlayerState {
        double lastX, lastY, lastZ;
        float lastMana;
        int ticksSinceLog;

        PlayerState(double x, double y, double z, float mana) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastMana = mana;
        }
    }
}
