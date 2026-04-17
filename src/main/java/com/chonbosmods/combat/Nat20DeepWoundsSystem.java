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
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deep Wounds affix: on melee hit, chance to apply the Nat20BleedEffect EntityEffect
 * to the target. The effect ticks through Hytale's native damage pipeline with a
 * Nat20Bleed DamageCause, so its hits are picked up by Nat20CombatParticleSystem
 * for the red bleed particle overlay.
 */
public class Nat20DeepWoundsSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:deep_wounds";
    private static final String EFFECT_ID = "Nat20BleedEffect";

    private static final float TICK_INTERVAL = 2.0f;
    // Affix value = per-tick damage at MAX duration; shorter rolls keep total
    // damage constant (= affix × BASE_TICKS), so per-tick scales up. Shorter
    // duration = higher DPS = more valuable roll.
    private static final float BASE_TICKS = Nat20DotTickSystem.MAX_DURATION / TICK_INTERVAL;

    private final Nat20LootSystem lootSystem;
    private final Nat20DotTickSystem dotTickSystem;
    private EntityEffect bleedEffect;
    private boolean effectResolved;

    public Nat20DeepWoundsSystem(Nat20LootSystem lootSystem, Nat20DotTickSystem dotTickSystem) {
        this.lootSystem = lootSystem;
        this.dotTickSystem = dotTickSystem;
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
        if (damage.isCancelled()) return;

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
        UUID attackerUuid = attackerPlayer != null ? attackerPlayer.getPlayerRef().getUuid() : null;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolledAffix : src.affixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) {
                    LOGGER.atWarning().log("[DeepWounds] affix def missing for %s", AFFIX_ID);
                    return;
                }

                double procChance = parseProcChance(def.procChance());
                double roll = ThreadLocalRandom.current().nextDouble();
                if (procChance <= 0) return;
                if (roll > procChance) return;

                if (!resolveEffect()) {
                    LOGGER.atWarning().log("[DeepWounds] effect '%s' unavailable; skipping proc", EFFECT_ID);
                    return;
                }

                Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
                EffectControllerComponent effectController =
                        store.getComponent(targetRef, EffectControllerComponent.getComponentType());
                if (effectController == null) {
                    LOGGER.atWarning().log("[DeepWounds] target has no EffectControllerComponent; skipping");
                    return;
                }

                ThreadLocalRandom rng = ThreadLocalRandom.current();
                float duration = rolledAffix.hasDuration()
                        ? (float) rolledAffix.duration()
                        : Nat20DotTickSystem.MAX_DURATION;
                float actualTicks = duration / TICK_INTERVAL;

                AffixValueRange dotRange = def.getValuesForRarity(src.rarity());
                float damagePerTick = 1.0f;
                if (dotRange != null) {
                    double rolledLevel = rolledAffix.rollLevel(rng);
                    double perTickAtBase = dotRange.interpolate(rolledLevel, src.ilvl(), src.qualityValue());
                    PlayerStats dotStats = attackerPlayer != null ? resolvePlayerStats(attackerRef, store) : null;
                    if (dotStats != null && def.statScaling() != null) {
                        Stat primary = def.statScaling().primary();
                        int mod = dotStats.getModifier(primary);
                        perTickAtBase *= (1.0 + mod * def.statScaling().factor());
                    }
                    double totalDamage = perTickAtBase * BASE_TICKS;
                    damagePerTick = (float) (totalDamage / actualTicks);
                }

                boolean isNew = dotTickSystem.registerDot(targetRef,
                        Nat20DotTickSystem.DotType.BLEED, attackerRef, damagePerTick, duration);

                if (isNew) {
                    Nat20EntityEffectUtil.applyOnce(effectController, targetRef, bleedEffect, duration, commandBuffer);
                }

                if (attackerUuid != null && CombatDebugSystem.isEnabled(attackerUuid)) {
                    LOGGER.atInfo().log("[DeepWounds] proc: attacker=%s dmg/tick=%.2f duration=%.1fs new=%s",
                            attackerUuid.toString().substring(0, 8), damagePerTick, duration, isNew);
                }
                return;
            }
        }
    }

    private boolean resolveEffect() {
        if (effectResolved) return bleedEffect != null;
        bleedEffect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        effectResolved = true;
        if (bleedEffect == null) {
            LOGGER.atWarning().log("[DeepWounds] EntityEffect '%s' not found in asset map", EFFECT_ID);
        }
        return bleedEffect != null;
    }

    private static double parseProcChance(@Nullable String procChanceStr) {
        if (procChanceStr == null || procChanceStr.isEmpty()) return 0.0;
        try {
            String trimmed = procChanceStr.strip();
            if (trimmed.endsWith("%")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) / 100.0;
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            LOGGER.atWarning().log("Failed to parse proc chance: '%s'", procChanceStr);
            return 0.0;
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
