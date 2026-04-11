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
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attack Speed affix system: ECS ticking system that applies interaction time shifts
 * to players wielding weapons with the attack_speed affix.
 *
 * <p>Runs AFTER {@code TickInteractionManagerSystem} each game tick, matching the
 * pattern used by AttackAnimationsFramework. This ensures the time shift is applied
 * after the engine's native interaction tick and persists until the next tick.
 */
public class Nat20AttackSpeedSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:attack_speed";
    // TODO: restore to 0.35 after testing
    private static final double SOFTCAP_K = 100.0;

    private final Nat20LootSystem lootSystem;
    private final ComponentType<EntityStore, InteractionManager> imType;
    private final Query<EntityStore> query;
    private final Set<Dependency<EntityStore>> dependencies;

    /** Track last applied shift per player to avoid redundant logging. */
    private final ConcurrentHashMap<UUID, Float> activeShifts = new ConcurrentHashMap<>();

    public Nat20AttackSpeedSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
        this.imType = InteractionModule.get().getInteractionManagerComponent();
        this.query = Query.and(new Query[]{
                Query.any(), UUIDComponent.getComponentType(), Player.getComponentType()
        });
        this.dependencies = Set.of(
                new SystemDependency<>(Order.AFTER, InteractionSystems.TickInteractionManagerSystem.class)
        );
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (dt <= 0.0f) return;

        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID playerUuid = uuidComp.getUuid();

        InteractionManager im = chunk.getComponent(index, imType);
        if (im == null) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        float bonus = computeAttackSpeedBonus(store, ref);

        if (bonus > 0) {
            // Apply global time shift to all interaction types every tick
            for (InteractionType type : EnumSet.allOf(InteractionType.class)) {
                im.setGlobalTimeShift(type, bonus);
            }

            Float previous = activeShifts.put(playerUuid, bonus);
            if (previous == null || Float.compare(previous, bonus) != 0) {
                if (CombatDebugSystem.isEnabled(playerUuid)) {
                    LOGGER.atInfo().log("[AttackSpeed] player=%s globalTimeShift=%.3f",
                            playerUuid.toString().substring(0, 8), bonus);
                }
            }
        } else {
            Float previous = activeShifts.remove(playerUuid);
            if (previous != null) {
                // Clear time shift
                for (InteractionType type : EnumSet.allOf(InteractionType.class)) {
                    im.setGlobalTimeShift(type, 0.0f);
                }
                if (CombatDebugSystem.isEnabled(playerUuid)) {
                    LOGGER.atInfo().log("[AttackSpeed] player=%s RESET",
                            playerUuid.toString().substring(0, 8));
                }
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

    /** Clean up tracked state on disconnect. */
    public void removePlayer(UUID uuid) {
        activeShifts.remove(uuid);
    }
}
