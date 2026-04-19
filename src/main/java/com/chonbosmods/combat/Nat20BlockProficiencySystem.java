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
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Block Proficiency: reduces stamina drain when blocking hits.
 * Filter Group system. On incoming blocked damage to player, scans weapon AND armor
 * for the affix and reduces the STAMINA_DRAIN_MULTIPLIER on the damage event.
 *
 * Detection: Damage.BLOCKED meta key indicates a blocked hit.
 * Effect: Damage.STAMINA_DRAIN_MULTIPLIER reduced by the affix percentage.
 */
public class Nat20BlockProficiencySystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:block_proficiency";
    private static final double SOFTCAP_K = 0.80;

    private final Nat20LootSystem lootSystem;

    public Nat20BlockProficiencySystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled()) return;

        // Only process blocked hits
        Boolean blocked = damage.getIfPresentMetaObject(Damage.BLOCKED);
        if (blocked == null || !blocked) return;

        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        double totalReduction = 0;

        // Check armor (includes shields)
        @SuppressWarnings("unchecked")
        CombinedItemContainer armorContainer = InventoryComponent.getCombined(
                store, targetRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armorContainer != null) {
            for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
                ItemStack item = armorContainer.getItemStack(slot);
                if (item == null || item.isEmpty()) continue;
                totalReduction += scanItemForAffix(item, affixRegistry, targetRef, store);
            }
        }

        if (totalReduction <= 0) return;
        totalReduction = Nat20Softcap.softcap(totalReduction, SOFTCAP_K);

        // Reduce stamina drain multiplier
        Float currentMultiplier = damage.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER);
        float base = (currentMultiplier != null) ? currentMultiplier : 1.0f;
        float reduced = (float) (base * (1.0 - totalReduction));
        if (reduced < 0f) reduced = 0f;
        damage.putMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER, reduced);

        UUID targetUuid = targetPlayer.getPlayerRef().getUuid();
        if (CombatDebugSystem.isEnabled(targetUuid)) {
            LOGGER.atInfo().log("[BlockProf] blocked hit: staminaDrain=%.2f->%.2f (reduction=%.1f%%)",
                    base, reduced, totalReduction * 100);
        }
    }

    private double scanItemForAffix(ItemStack item, Nat20AffixRegistry affixRegistry,
                                     Ref<EntityStore> playerRef, Store<EntityStore> store) {
        Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return 0;

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return 0;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return 0;

            double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, lootSystem.getRarityRegistry());
            double effectiveValue = baseValue;
            PlayerStats stats = resolvePlayerStats(playerRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getPowerModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }
            return effectiveValue;
        }
        return 0;
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
