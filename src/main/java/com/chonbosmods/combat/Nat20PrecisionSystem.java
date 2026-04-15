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
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * Armor Penetration: scans the attacker's weapon for the precision affix,
 * reads the target's equipped armor to compute vanilla damage resistance,
 * then adds back a percentage of what that armor would block.
 * DEX-scaled, softcapped.
 */
public class Nat20PrecisionSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:precision";
    private static final double SOFTCAP_K = 0.40;

    private final Nat20LootSystem lootSystem;

    public Nat20PrecisionSystem(Nat20LootSystem lootSystem) {
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
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        // Skip DOT tick damage
        if (Nat20DotTickSystem.isDotTickDamage(damage)) return;

        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        ItemStack weapon = InventoryComponent.getItemInHand(store, attackerRef);
        if (weapon == null || weapon.isEmpty()) return;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return;

            double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, lootSystem.getRarityRegistry());
            double effectiveValue = baseValue;
            PlayerStats stats = resolvePlayerStats(attackerRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }
            effectiveValue = Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);

            // Read the target's armor to compute vanilla resistance for this damage cause
            Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
            double armorReduction = computeArmorReduction(store, targetRef, damage);
            if (armorReduction <= 0) return;

            // Add back the penetrated portion
            float original = damage.getAmount();
            float boosted = (float) (original + armorReduction * effectiveValue);
            damage.setAmount(boosted);

            UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[Precision] player=%s pen=%.1f%% armorBlock=%.1f restored=+%.1f damage=%.1f->%.1f",
                        attackerUuid.toString().substring(0, 8),
                        effectiveValue * 100, armorReduction,
                        armorReduction * effectiveValue, original, boosted);
            }
            return;
        }
    }

    /**
     * Computes how much damage the target's vanilla armor would block for this damage cause.
     * Replicates the logic from DamageSystems.ArmorDamageReduction.getResistanceModifiers:
     * sums flat (ADDITIVE + baseDamageResistance) and multiplicative modifiers across all
     * armor pieces, then applies: blocked = initialDmg - max(0, initialDmg - flat) * max(0, 1 - mult)
     */
    private double computeArmorReduction(Store<EntityStore> store, Ref<EntityStore> targetRef, Damage damage) {
        @SuppressWarnings("unchecked")
        CombinedItemContainer armorContainer = InventoryComponent.getCombined(
                store, targetRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armorContainer == null) return 0;

        DamageCause damageCause = damage.getCause();
        if (damageCause == null || damageCause.doesBypassResistances()) return 0;

        int flatTotal = 0;
        float multTotal = 0f;

        for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
            ItemStack itemStack = armorContainer.getItemStack(slot);
            if (itemStack == null || itemStack.isEmpty()) continue;

            Item item = itemStack.getItem();
            ItemArmor armor = item.getArmor();
            if (armor == null) continue;

            Map<DamageCause, StaticModifier[]> resistances = armor.getDamageResistanceValues();
            if (resistances == null) continue;

            StaticModifier[] mods = resistances.get(damageCause);
            if (mods == null) continue;

            double flatResistance = armor.getBaseDamageResistance();

            for (StaticModifier mod : mods) {
                if (mod.getCalculationType() == StaticModifier.CalculationType.ADDITIVE) {
                    flatTotal += (int) mod.getAmount();
                } else {
                    multTotal += mod.getAmount();
                }
            }
            flatTotal += (int) flatResistance;
        }

        if (flatTotal <= 0 && multTotal <= 0f) return 0;

        // Same formula as vanilla ArmorDamageReduction
        float initialDmg = damage.getInitialAmount();
        float afterFlat = Math.max(0f, initialDmg - flatTotal);
        float afterMult = afterFlat * Math.max(0f, 1f - multTotal);

        return initialDmg - afterMult;
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
