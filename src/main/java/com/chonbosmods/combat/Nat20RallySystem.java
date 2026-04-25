package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20AffixScaling;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rally: on kill, nearby allies receive a temporary damage bonus EntityEffect.
 * Kill detection: checks if target health will reach 0 after this damage.
 * Spatial query for nearby players within radius.
 */
public class Nat20RallySystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:rally";
    private static final String EFFECT_ID = "Nat20RallyEffect";
    private static final double SOFTCAP_K = 0.40;
    private static final double RALLY_RADIUS = 20.0;

    private final Nat20LootSystem lootSystem;
    private EntityEffect rallyEffect;
    private boolean effectResolved;
    private int healthIdx = Integer.MIN_VALUE;
    private boolean statResolved;

    // Track rallied players: UUID -> RallyState
    private final ConcurrentHashMap<UUID, RallyState> ralliedPlayers = new ConcurrentHashMap<>();

    record RallyState(double damageBonus, long expiryMs) {}

    public Nat20RallySystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        // Skip DOT tick damage: weapon affixes should not re-trigger on periodic damage
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

            // Kill detection: check if this damage will kill the target
            if (!statResolved) {
                healthIdx = EntityStatType.getAssetMap().getIndex("Health");
                statResolved = true;
            }
            if (healthIdx < 0) return;

            Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
            EntityStatMap targetStats = store.getComponent(targetRef, EntityStatMap.getComponentType());
            if (targetStats == null) return;

            float targetHP = targetStats.get(healthIdx).get();
            if (targetHP > damage.getAmount()) return; // Not a kill

            // This is a kill! Apply rally to nearby allies
            if (!resolveEffect()) return;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return;

            // Compute rally bonus
            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return;

            double baseValue = Nat20AffixScaling.interpolate(range, rolledAffix.midLevel(), lootData, lootSystem.getRarityRegistry(), def.ilvlScalable());
            double effectiveValue = baseValue;
            PlayerStats stats = resolvePlayerStats(attackerRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getPowerModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }
            effectiveValue = Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);

            UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
            TransformComponent attackerTransform = store.getComponent(attackerRef, TransformComponent.getComponentType());
            if (attackerTransform == null) return;

            Vector3d attackerPos = attackerTransform.getPosition();
            long expiryMs = System.currentTimeMillis() + 12000; // 12s matches EntityEffect Duration

            // Find nearby allied players
            List<Ref<EntityStore>> nearby = TargetUtil.getAllEntitiesInSphere(attackerPos, RALLY_RADIUS, store);
            int buffed = 0;

            for (Ref<EntityStore> allyRef : nearby) {
                if (!allyRef.isValid()) continue;
                if (allyRef.equals(attackerRef)) continue;

                Player allyPlayer = store.getComponent(allyRef, Player.getComponentType());
                if (allyPlayer == null) continue;

                UUID allyUuid = allyPlayer.getPlayerRef().getUuid();

                // Apply visual effect (replace-not-stack)
                RallyState previous = ralliedPlayers.put(allyUuid,
                        new RallyState(effectiveValue, expiryMs));
                boolean isNew = previous == null || System.currentTimeMillis() > previous.expiryMs;
                if (isNew) {
                    EffectControllerComponent effectCtrl =
                            store.getComponent(allyRef, EffectControllerComponent.getComponentType());
                    Nat20EntityEffectUtil.applyOnce(effectCtrl, allyRef, rallyEffect, commandBuffer);
                }
                buffed++;
            }

            if (CombatDebugSystem.isEnabled(attackerUuid)) {
                LOGGER.atInfo().log("[Rally] kill confirmed: bonus=+%.1f%% buffed=%d allies within %.0f blocks",
                        effectiveValue * 100, buffed, RALLY_RADIUS);
            }
            return;
        }
    }

    /**
     * Called by Nat20RallyAmplifySystem. Returns damage bonus if player has active rally buff.
     */
    public double getRallyBonus(UUID playerUuid) {
        RallyState state = ralliedPlayers.get(playerUuid);
        if (state == null) return 0;
        if (System.currentTimeMillis() > state.expiryMs) {
            ralliedPlayers.remove(playerUuid);
            return 0;
        }
        return state.damageBonus;
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

    private boolean resolveEffect() {
        if (effectResolved) return rallyEffect != null;
        rallyEffect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        effectResolved = true;
        if (rallyEffect == null) {
            LOGGER.atWarning().log("[Rally] EntityEffect '%s' not found", EFFECT_ID);
        }
        return rallyEffect != null;
    }
}
