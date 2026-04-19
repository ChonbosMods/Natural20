package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.EffectAffixSource;
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
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Crushing Blow: on hit, drains a percentage of the target's current HP.
 * Uses subtractStatValue directly (bypasses damage pipeline).
 */
public class Nat20CrushingBlowSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:crushing_blow";
    private static final String PARTICLE = "Nat20_CrushingBlow";
    private static final double SOFTCAP_K = 0.20;
    private static final float TORSO_OFFSET_Y = 0.9f;

    private final Nat20LootSystem lootSystem;
    private int healthIdx = Integer.MIN_VALUE;
    private boolean statResolved;

    public Nat20CrushingBlowSystem(Nat20LootSystem lootSystem) {
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

        List<EffectAffixSource.Source> sources = EffectAffixSource.resolveAttackerSources(
                attackerRef, store, lootSystem);
        if (sources.isEmpty()) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolledAffix : src.affixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) return;

                AffixValueRange range = def.getValuesForRarity(src.rarity());
                if (range == null) return;

                double basePercent = range.interpolate(rolledAffix.midLevel(), src.ilvl(), src.qualityValue());
                double effectivePercent = basePercent;
                PlayerStats stats = attackerPlayer != null ? resolvePlayerStats(attackerRef, store) : null;
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getPowerModifier(primary);
                    effectivePercent = basePercent * (1.0 + modifier * def.statScaling().factor());
                }
                effectivePercent = Nat20Softcap.softcap(effectivePercent, SOFTCAP_K);

                if (!statResolved) {
                    healthIdx = EntityStatType.getAssetMap().getIndex("Health");
                    statResolved = true;
                }
                if (healthIdx < 0) return;

                Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
                EntityStatMap targetStats = store.getComponent(targetRef, EntityStatMap.getComponentType());
                if (targetStats == null) return;

                float currentHP = targetStats.get(healthIdx).get();
                float drain = (float) (currentHP * effectivePercent);
                if (drain <= 0f) return;

                targetStats.subtractStatValue(healthIdx, drain);

                TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d pos = transform.getPosition();
                    try {
                        ParticleUtil.spawnParticleEffect(PARTICLE,
                                new Vector3d(pos.getX(), pos.getY() + TORSO_OFFSET_Y, pos.getZ()), store);
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("[CrushingBlow] particle failed");
                    }
                }

                if (attackerPlayer != null) {
                    UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
                    if (CombatDebugSystem.isEnabled(attackerUuid)) {
                        LOGGER.atInfo().log("[CrushingBlow] player=%s targetHP=%.1f drain=%.1f (%.1f%%)",
                                attackerUuid.toString().substring(0, 8), currentHP, drain, effectivePercent * 100);
                    }
                }
                return;
            }
        }
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
