package com.chonbosmods.loot.effects;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.AffixType;
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
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Central event listener that fires EFFECT and ABILITY affix handlers on combat and mining events.
 *
 * <p>Uses the ECS event system to listen for {@link Damage} and {@link BreakBlockEvent} on
 * entities. For each event, inspects the relevant player's equipped items for Nat20 loot data,
 * computes effective affix values (base interpolation + stat scaling), and delegates to registered
 * {@link EffectHandler} instances via {@link EffectHandlerRegistry}.
 *
 * <p>The Damage system handles two paths:
 * <ul>
 *   <li><b>Target (onHurt):</b> The ECS entity receiving damage is checked for armor affixes.</li>
 *   <li><b>Attacker (onHit):</b> The damage source is checked for weapon affixes via
 *       {@link Damage.EntitySource#getRef()}.</li>
 * </ul>
 */
public class Nat20AffixEventListener {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Nat20LootSystem lootSystem;

    public Nat20AffixEventListener(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    /**
     * Register both ECS event systems with the entity store registry.
     * Call this during plugin setup.
     */
    public void register(ComponentRegistryProxy<EntityStore> entityStoreRegistry) {
        entityStoreRegistry.registerSystem(new Nat20DamageSystem());
        entityStoreRegistry.registerSystem(new Nat20BlockBreakSystem());
    }

    // ---- Damage ECS system (fires on the entity being damaged) ----

    private class Nat20DamageSystem extends DamageEventSystem {

        private static final Query<EntityStore> QUERY = Query.any();

        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Override
        public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                           Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                           Damage damage) {
            if (damage.isCancelled()) return;

            Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);

            // Attacker path: check if the source is a player and fire onHit for weapon affixes
            handleAttackerAffixes(damage, store);

            // Target path: check if the damaged entity is a player and fire onHurt for armor affixes
            handleTargetAffixes(damage, targetRef, store);
        }
    }

    /**
     * When a player attacks, iterate the weapon's EFFECT/ABILITY affixes and fire onHit.
     */
    private void handleAttackerAffixes(Damage damage, Store<EntityStore> store) {
        Player attacker = extractPlayerFromSource(damage.getSource(), store);
        if (attacker == null) return;

        PlayerStats playerStats = resolvePlayerStats(attacker.getReference(), store);

        // Get the player's active hotbar item (the weapon used to attack)
        ItemStack weapon = attacker.getInventory().getActiveHotbarItem();
        if (weapon == null || weapon.isEmpty()) return;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        processAffixes(lootData, playerStats, (handler, def, effectiveValue) ->
                handler.onHit(def, effectiveValue, damage));
    }

    /**
     * When a player is hurt, iterate each armor piece's EFFECT/ABILITY affixes and fire onHurt.
     */
    private void handleTargetAffixes(Damage damage, Ref<EntityStore> targetRef,
                                      Store<EntityStore> store) {
        Player target = store.getComponent(targetRef, Player.getComponentType());
        if (target == null) return;

        PlayerStats playerStats = resolvePlayerStats(targetRef, store);

        Inventory inventory = target.getInventory();
        ItemContainer armorContainer = inventory.getArmor();
        short armorCapacity = armorContainer.getCapacity();

        for (short slot = 0; slot < armorCapacity; slot++) {
            ItemStack armorPiece = armorContainer.getItemStack(slot);
            if (armorPiece == null || armorPiece.isEmpty()) continue;

            Nat20LootData lootData = armorPiece.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) continue;

            processAffixes(lootData, playerStats, (handler, def, effectiveValue) ->
                    handler.onHurt(def, effectiveValue, damage));
        }
    }

    // ---- Block break ECS system (fires on the entity breaking the block) ----

    private class Nat20BlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        private static final Query<EntityStore> QUERY = Query.any();

        Nat20BlockBreakSystem() {
            super(BreakBlockEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Override
        public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                           Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                           BreakBlockEvent event) {
            if (event.isCancelled()) return;

            Ref<EntityStore> playerRef = chunk.getReferenceTo(entityIndex);
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;

            PlayerStats playerStats = resolvePlayerStats(playerRef, store);

            ItemStack tool = event.getItemInHand();
            if (tool == null || tool.isEmpty()) return;

            Nat20LootData lootData = tool.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
            if (lootData == null) return;

            processAffixes(lootData, playerStats, (handler, def, effectiveValue) ->
                    handler.onBlockBreak(def, effectiveValue, event));
        }
    }

    // ---- Shared affix processing ----

    /**
     * Iterate all EFFECT and ABILITY affixes on an item's loot data, compute effective values,
     * look up the handler, and invoke the callback.
     */
    private void processAffixes(Nat20LootData lootData, @Nullable PlayerStats playerStats,
                                 AffixCallback callback) {
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        EffectHandlerRegistry handlerRegistry = lootSystem.getEffectHandlerRegistry();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            Nat20AffixDef def = affixRegistry.get(rolledAffix.id());
            if (def == null) continue;

            // Only process EFFECT and ABILITY affixes: STAT affixes are handled by the modifier manager
            if (def.type() != AffixType.EFFECT && def.type() != AffixType.ABILITY) continue;

            EffectHandler handler = handlerRegistry.get(rolledAffix.id());
            if (handler == null) continue;

            double effectiveValue = computeEffectiveValue(def, lootData, playerStats);
            try {
                callback.invoke(handler, def, effectiveValue);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Effect handler threw for affix '%s'",
                        rolledAffix.id());
            }
        }
    }

    /**
     * Compute the effective value for an affix: interpolate from the value range at the item's
     * rarity using loot level, then apply stat scaling if the player has stats.
     */
    private double computeEffectiveValue(Nat20AffixDef def, Nat20LootData lootData,
                                          @Nullable PlayerStats playerStats) {
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

    // ---- Helpers ----

    /**
     * Attempt to extract a Player from a Damage.Source.
     *
     * <p>{@link Damage.EntitySource} provides {@code getRef()} which returns a
     * {@link Ref} to the attacking entity in the entity store. We resolve the Player
     * component from that ref.
     */
    @Nullable
    private Player extractPlayerFromSource(@Nullable Damage.Source source, Store<EntityStore> store) {
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> attackerRef = entitySource.getRef();
            return store.getComponent(attackerRef, Player.getComponentType());
        }
        return null;
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
            LOGGER.atWarning().withCause(e).log("Failed to resolve player stats");
            return null;
        }
    }

    @FunctionalInterface
    private interface AffixCallback {
        void invoke(EffectHandler handler, Nat20AffixDef def, double effectiveValue);
    }
}
