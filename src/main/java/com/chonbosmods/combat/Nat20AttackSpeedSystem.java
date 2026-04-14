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
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionEntry;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attack Speed affix system: ECS ticking system that applies interaction time shifts
 * to players wielding weapons with the attack_speed affix.
 *
 * <p>Runs AFTER {@code TickInteractionManagerSystem} each game tick. Applies server-side
 * timing controls (timeShift, timestamp, sync nudge) that make the interaction complete
 * faster. At production values (10-22% speed boost), the tail-end animation frames get
 * trimmed imperceptibly.
 */
public class Nat20AttackSpeedSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:attack_speed";
    private static final double SOFTCAP_K = 0.35;
    private static final long CHAIN_SYNC_COOLDOWN_MS = 24L;

    private final Nat20LootSystem lootSystem;
    private final Query<EntityStore> query;
    private final Set<Dependency<EntityStore>> dependencies;

    /** Lazily resolved: InteractionModule may not be ready during setup(). */
    private volatile ComponentType<EntityStore, InteractionManager> imType;

    /** Track last applied shift per player to avoid redundant logging. */
    private final ConcurrentHashMap<UUID, Float> activeShifts = new ConcurrentHashMap<>();

    /** Track last sync nudge timestamp per player. */
    private final ConcurrentHashMap<UUID, Long> lastChainSyncAtMs = new ConcurrentHashMap<>();

    public Nat20AttackSpeedSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
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

        if (imType == null) {
            try {
                imType = InteractionModule.get().getInteractionManagerComponent();
            } catch (Exception e) {
                return;
            }
        }

        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID playerUuid = uuidComp.getUuid();

        InteractionManager im = chunk.getComponent(index, imType);
        if (im == null) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        float bonus = computeAttackSpeedBonus(store, ref);

        if (bonus > 0) {
            // Global time shift: affects all future chains for this player
            for (InteractionType type : EnumSet.allOf(InteractionType.class)) {
                im.setGlobalTimeShift(type, bonus);
            }

            // Per-chain: timeShift + timestamp + meta + sync nudge
            Map<Integer, InteractionChain> chains = im.getChains();
            if (chains != null && !chains.isEmpty()) {
                long now = System.currentTimeMillis();
                for (InteractionChain chain : chains.values()) {
                    if (chain == null || chain.getServerState() != InteractionState.NotFinished) continue;

                    chain.setTimeShift(bonus);

                    InteractionEntry entry = resolveEntry(chain);
                    if (entry == null) continue;

                    try {
                        entry.getMetaStore().putMetaObject(Interaction.TIME_SHIFT, bonus);
                    } catch (Throwable ignored) {}

                    try {
                        long ts = entry.getTimestamp();
                        if (ts > 0L) {
                            entry.setTimestamp(ts, bonus * dt);
                        }
                    } catch (Throwable ignored) {}

                    long lastSync = lastChainSyncAtMs.getOrDefault(playerUuid, 0L);
                    if (now - lastSync >= CHAIN_SYNC_COOLDOWN_MS) {
                        try {
                            InteractionSyncData server = entry.getServerState();
                            if (server != null) {
                                List<InteractionSyncData> updates = new ArrayList<>(1);
                                updates.add(server.clone());
                                im.sendSyncPacket(chain, entry.getIndex(), updates);
                                lastChainSyncAtMs.put(playerUuid, now);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            Float previous = activeShifts.put(playerUuid, bonus);
            if (previous == null || Float.compare(previous, bonus) != 0) {
                if (CombatDebugSystem.isEnabled(playerUuid)) {
                    LOGGER.atInfo().log("[AttackSpeed] player=%s timeShift=%.3f (applied)",
                            playerUuid.toString().substring(0, 8), bonus);
                }
            }
        } else {
            Float previous = activeShifts.remove(playerUuid);
            lastChainSyncAtMs.remove(playerUuid);
            if (previous != null) {
                for (InteractionType type : EnumSet.allOf(InteractionType.class)) {
                    im.setGlobalTimeShift(type, 0.0f);
                }
                Map<Integer, InteractionChain> chains = im.getChains();
                if (chains != null) {
                    for (InteractionChain chain : chains.values()) {
                        chain.setTimeShift(0.0f);
                    }
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

            double baseValue = range.interpolate(rolledAffix.midLevel());
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

    private static InteractionEntry resolveEntry(InteractionChain chain) {
        if (chain == null) return null;
        int base = chain.getOperationIndex();
        InteractionEntry entry = chain.getInteraction(base);
        if (entry != null) return entry;
        for (int delta = 1; delta <= 8; delta++) {
            entry = chain.getInteraction(base - delta);
            if (entry != null) return entry;
            entry = chain.getInteraction(base + delta);
            if (entry != null) return entry;
        }
        return null;
    }

    public void removePlayer(UUID uuid) {
        activeShifts.remove(uuid);
        lastChainSyncAtMs.remove(uuid);
    }
}
