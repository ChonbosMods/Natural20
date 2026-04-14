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
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Resilience: reduces the remaining duration of all active debuffs on the player.
 * EntityTickingSystem that runs every tick. Scans armor for resilience affix,
 * then drains extra time from any active debuff EntityEffects.
 *
 * Uses reflection to modify ActiveEntityEffect.remainingDuration (protected field,
 * no public setter).
 */
public class Nat20ResilienceSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:resilience";
    private static final double SOFTCAP_K = 0.60;
    private static final int LOG_INTERVAL_TICKS = 20;

    private final Nat20LootSystem lootSystem;
    private final Query<EntityStore> query;
    private Field remainingDurationField;
    private boolean fieldResolved;
    private int tickCounter;

    public Nat20ResilienceSystem(Nat20LootSystem lootSystem) {
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

        if (!fieldResolved) {
            try {
                remainingDurationField = ActiveEntityEffect.class.getDeclaredField("remainingDuration");
                remainingDurationField.setAccessible(true);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("[Resilience] failed to resolve remainingDuration field");
            }
            fieldResolved = true;
        }
        if (remainingDurationField == null) return;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;

        EffectControllerComponent effectCtrl =
                store.getComponent(playerRef, EffectControllerComponent.getComponentType());
        if (effectCtrl == null) return;

        // Check if any debuffs are active before scanning armor
        ActiveEntityEffect[] activeEffects = effectCtrl.getAllActiveEntityEffects();
        if (activeEffects == null || activeEffects.length == 0) return;

        boolean hasDebuff = false;
        for (ActiveEntityEffect ae : activeEffects) {
            if (ae.isDebuff() && !ae.isInfinite() && ae.getRemainingDuration() > 0) {
                hasDebuff = true;
                break;
            }
        }
        if (!hasDebuff) return;

        // Scan armor for resilience
        double totalResilience = scanArmorForResilience(playerRef, store);
        if (totalResilience <= 0) return;
        totalResilience = Nat20Softcap.softcap(totalResilience, SOFTCAP_K);

        // Drain extra duration from all active debuffs
        // Extra drain per tick = dt * resilienceMultiplier
        // (e.g., 30% resilience = debuffs tick 30% faster toward expiry)
        float extraDrain = (float) (dt * totalResilience);

        for (ActiveEntityEffect ae : activeEffects) {
            if (!ae.isDebuff() || ae.isInfinite() || ae.getRemainingDuration() <= 0) continue;

            try {
                float current = ae.getRemainingDuration();
                float reduced = Math.max(0f, current - extraDrain);
                remainingDurationField.setFloat(ae, reduced);
            } catch (Exception e) {
                LOGGER.atWarning().log("[Resilience] failed to set remainingDuration: %s", e.getMessage());
            }
        }

        tickCounter++;
        if (tickCounter >= LOG_INTERVAL_TICKS) {
            tickCounter = 0;
            UUID uuid = player.getPlayerRef().getUuid();
            if (CombatDebugSystem.isEnabled(uuid)) {
                LOGGER.atInfo().log("[Resilience] player=%s resilience=%.1f%% extraDrain=%.4f/tick debuffs=%d",
                        uuid.toString().substring(0, 8), totalResilience * 100, extraDrain, activeEffects.length);
            }
        }
    }

    private double scanArmorForResilience(Ref<EntityStore> playerRef, Store<EntityStore> store) {
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

                double baseValue = range.interpolate(rolledAffix.midLevel());
                double effectiveValue = baseValue;
                PlayerStats stats = resolvePlayerStats(playerRef, store);
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getModifier(primary);
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
