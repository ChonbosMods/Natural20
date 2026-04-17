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
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thorns: returns flat damage to melee attackers. Ignores Nat20Thorns cause
 * to prevent infinite ping-pong.
 */
public class Nat20ThornsSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:thorns";
    private static final String PARTICLE = "Nat20_Thorns";
    private static final double SOFTCAP_K = 50.0;
    private static final double PROC_CHANCE = 0.50;
    private static final float TORSO_OFFSET_Y = 0.9f;

    private final Nat20LootSystem lootSystem;
    private int thornsCauseIdx = Integer.MIN_VALUE;
    private boolean causeResolved;

    public Nat20ThornsSystem(Nat20LootSystem lootSystem) {
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

        if (!causeResolved) {
            thornsCauseIdx = DamageCause.getAssetMap().getIndex("Nat20Thorns");
            causeResolved = true;
        }

        // Prevent infinite thorns ping-pong
        if (thornsCauseIdx >= 0 && damage.getDamageCauseIndex() == thornsCauseIdx) return;

        Ref<EntityStore> defenderRef = chunk.getReferenceTo(entityIndex);

        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        List<EffectAffixSource.Source> sources = EffectAffixSource.resolveDefenderSources(
                defenderRef, store, lootSystem);
        if (sources.isEmpty()) return;

        Player defenderPlayer = store.getComponent(defenderRef, Player.getComponentType());
        PlayerStats stats = defenderPlayer != null ? resolvePlayerStats(defenderRef, store) : null;
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        double totalThornsDamage = 0;

        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolledAffix : src.affixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) continue;

                AffixValueRange range = def.getValuesForRarity(src.rarity());
                if (range == null) continue;

                double baseValue = range.interpolate(
                        rolledAffix.rollLevel(ThreadLocalRandom.current()),
                        src.ilvl(), src.qualityValue());
                double effectiveValue = baseValue;
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getModifier(primary);
                    effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
                }
                totalThornsDamage += effectiveValue;
            }
        }

        if (totalThornsDamage <= 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= PROC_CHANCE) return;
        totalThornsDamage = Nat20Softcap.softcap(totalThornsDamage, SOFTCAP_K);

        if (thornsCauseIdx < 0) return;

        commandBuffer.invoke(attackerRef,
                new Damage(new Damage.EntitySource(defenderRef), thornsCauseIdx, (float) totalThornsDamage));

        TransformComponent transform = store.getComponent(attackerRef, TransformComponent.getComponentType());
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            try {
                ParticleUtil.spawnParticleEffect(PARTICLE,
                        new Vector3d(pos.getX(), pos.getY() + TORSO_OFFSET_Y, pos.getZ()), store);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("[Thorns] particle failed");
            }
        }

        if (defenderPlayer != null) {
            UUID defenderUuid = defenderPlayer.getPlayerRef().getUuid();
            if (CombatDebugSystem.isEnabled(defenderUuid)) {
                LOGGER.atInfo().log("[Thorns] reflected %.1f damage to attacker=%s",
                        totalThornsDamage, attackerRef);
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
