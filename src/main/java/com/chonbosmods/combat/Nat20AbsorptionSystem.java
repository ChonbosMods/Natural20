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
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Absorption affix system: intercepts incoming damage to players in the Filter Group,
 * redirects a portion to mana, and reduces the damage taken. Fixed 3-second cooldown
 * between activations.
 */
public class Nat20AbsorptionSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:absorption";
    private static final long COOLDOWN_MS = 5000L;

    private final Nat20LootSystem lootSystem;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public Nat20AbsorptionSystem(Nat20LootSystem lootSystem) {
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

        // Only process players, not NPCs
        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer == null) return;

        UUID playerUuid = targetPlayer.getPlayerRef().getUuid();

        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastActivation = cooldowns.get(playerUuid);
        if (lastActivation != null && (now - lastActivation) < COOLDOWN_MS) return;

        // Resolve player stats for WIS scaling
        PlayerStats playerStats = resolvePlayerStats(targetRef, store);

        // Scan all equipped items for the absorption affix
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        double totalRaw = 0.0;

        // Weapon in hand
        ItemStack weapon = InventoryComponent.getItemInHand(store, targetRef);
        if (weapon != null && !weapon.isEmpty()) {
            totalRaw += scanItemForAbsorption(weapon, affixRegistry, playerStats);
        }

        // All armor slots
        @SuppressWarnings("unchecked")
        CombinedItemContainer armorContainer = InventoryComponent.getCombined(
                store, targetRef, new ComponentType[]{InventoryComponent.Armor.getComponentType()});
        if (armorContainer != null) {
            short armorCapacity = armorContainer.getCapacity();
            for (short slot = 0; slot < armorCapacity; slot++) {
                ItemStack armorPiece = armorContainer.getItemStack(slot);
                if (armorPiece == null || armorPiece.isEmpty()) continue;
                totalRaw += scanItemForAbsorption(armorPiece, affixRegistry, playerStats);
            }
        }

        if (totalRaw <= 0.0) return;

        // Apply softcap (k=0.50, caps around 35-40%)
        double effectiveAbsorption = Nat20Softcap.softcap(totalRaw, 0.50);

        // Compute absorbed damage
        float incomingDamage = damage.getAmount();
        double absorbedDamage = incomingDamage * effectiveAbsorption;

        // Cap to available mana
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int manaIdx = EntityStatType.getAssetMap().getIndex("Mana");
        if (manaIdx < 0) return;

        float currentMana = statMap.get(manaIdx).get();
        if (currentMana <= 0) return;

        if (absorbedDamage > currentMana) {
            absorbedDamage = currentMana;
        }

        // Drain mana
        statMap.subtractStatValue(manaIdx, (float) absorbedDamage);

        // Reduce damage
        damage.setAmount((float) (incomingDamage - absorbedDamage));

        // Record cooldown
        cooldowns.put(playerUuid, now);

        // Debug logging
        if (CombatDebugSystem.isEnabled(playerUuid)) {
            float remainingMana = statMap.get(manaIdx).get();
            LOGGER.atInfo().log("[Absorption] player=%s raw=%.3f effective=%.3f incoming=%.1f absorbed=%.1f mana=%.0f->%.0f",
                    playerUuid.toString().substring(0, 8),
                    totalRaw, effectiveAbsorption,
                    incomingDamage, absorbedDamage,
                    currentMana, remainingMana);
        }
    }

    /**
     * Scan a single item for the absorption affix and compute its effective value.
     */
    private double scanItemForAbsorption(ItemStack item, Nat20AffixRegistry affixRegistry,
                                          PlayerStats playerStats) {
        Nat20LootData lootData = item.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return 0.0;

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return 0.0;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return 0.0;

            double baseValue = range.interpolate(rolledAffix.midLevel());
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
     * Remove a player's cooldown state on disconnect.
     */
    public void removePlayer(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
