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
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water Breathing: increases max oxygen via StaticModifier on the Oxygen stat.
 * EntityTickingSystem that tracks equipped resilience and applies/removes the modifier.
 * Same proven pattern as CON → MaxHP in Nat20ScoreBonusSystem.
 */
public class Nat20WaterBreathingSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:water_breathing";
    private static final String MODIFIER_KEY = "nat20:water_breathing";
    private static final double SOFTCAP_K = 5.0;
    private static final int CHECK_INTERVAL_TICKS = 20; // Only recheck every ~1 second

    private final Nat20LootSystem lootSystem;
    private final Query<EntityStore> query;
    private final ConcurrentHashMap<UUID, Double> lastApplied = new ConcurrentHashMap<>();

    private int oxygenIdx = Integer.MIN_VALUE;
    private boolean statResolved;
    private int tickCounter;

    public Nat20WaterBreathingSystem(Nat20LootSystem lootSystem) {
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
        // Only check periodically
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;

        if (!statResolved) {
            oxygenIdx = EntityStatType.getAssetMap().getIndex("Oxygen");
            statResolved = true;
            if (oxygenIdx < 0) {
                LOGGER.atWarning().log("[WaterBreathing] Oxygen stat not found");
            }
        }
        if (oxygenIdx < 0) return;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID uuid = uuidComp.getUuid();

        EntityStatMap statMap = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        double totalBonus = scanArmorForAffix(playerRef, store);
        if (totalBonus > 0) {
            totalBonus = Nat20Softcap.softcap(totalBonus, SOFTCAP_K);
        }

        Double previous = lastApplied.get(uuid);
        double prevValue = (previous != null) ? previous : 0;

        // Skip if unchanged
        if (Math.abs(totalBonus - prevValue) < 0.001) return;

        // Snapshot current oxygen before changing max
        float currentOxygen = statMap.get(oxygenIdx).get();

        // Remove old modifier and apply new one
        statMap.removeModifier(oxygenIdx, MODIFIER_KEY);
        if (totalBonus > 0) {
            float bonusAmount = (float) (100.0 * totalBonus);
            statMap.putModifier(oxygenIdx, MODIFIER_KEY, new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE, bonusAmount));
            lastApplied.put(uuid, totalBonus);
        } else {
            lastApplied.remove(uuid);
        }

        // Restore oxygen to what it was (clamped to new max by the engine)
        float newMax = statMap.get(oxygenIdx).getMax();
        statMap.setStatValue(oxygenIdx, Math.min(currentOxygen, newMax));

        if (CombatDebugSystem.isEnabled(uuid)) {
            LOGGER.atInfo().log("[WaterBreathing] player=%s bonus=+%.0f%% maxOxygen=%.0f",
                    uuid.toString().substring(0, 8), totalBonus * 100,
                    100.0 + 100.0 * totalBonus);
        }
    }

    public void removePlayer(UUID uuid) {
        lastApplied.remove(uuid);
    }

    private double scanArmorForAffix(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        @SuppressWarnings("unchecked")
        CombinedItemContainer armorContainer = InventoryComponent.getCombined(
                store, playerRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armorContainer == null) return 0;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        double total = 0;

        for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
            ItemStack item = armorContainer.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) continue;

            for (RolledAffix rolledAffix : lootData.getAffixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) continue;

                AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
                if (range == null) continue;

                double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, lootSystem.getRarityRegistry());
                double effectiveValue = baseValue;
                PlayerStats stats = resolvePlayerStats(playerRef, store);
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getPowerModifier(primary);
                    effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
                }
                total += effectiveValue;
            }
        }
        return total;
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
