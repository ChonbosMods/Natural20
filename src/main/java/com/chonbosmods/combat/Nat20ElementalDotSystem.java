package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.EffectAffixSource;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
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
 * Elemental proc DOT affixes: ignite, cold, infect, corrupt.
 * On player melee hit, rolls WIS-scaled proc chance. On proc, applies the matching
 * EntityEffect via EffectControllerComponent.addEffect(). Reapplication refreshes
 * duration (no stacking). Same pattern as Nat20DeepWoundsSystem.
 */
public class Nat20ElementalDotSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private static final String IGNITE_ID = "nat20:ignite";
    private static final String COLD_ID = "nat20:cold";
    private static final String INFECT_ID = "nat20:infect";
    private static final String CORRUPT_ID = "nat20:corrupt";

    private static final String IGNITE_EFFECT = "Nat20IgniteEffect";
    private static final String COLD_EFFECT = "Nat20ColdEffect";
    private static final String INFECT_EFFECT = "Nat20InfectEffect";
    private static final String CORRUPT_EFFECT = "Nat20CorruptEffect";

    private static final float TICK_INTERVAL = 2.0f;
    // Affix value represents per-tick damage at MAX duration (15s). Shorter rolls
    // preserve total damage (affix × BASE_TICKS), so per-tick scales up as duration
    // shrinks — shorter duration = higher DPS = more valuable roll.
    private static final float BASE_TICKS = Nat20DotTickSystem.MAX_DURATION / TICK_INTERVAL;

    private final Nat20LootSystem lootSystem;
    private final Nat20DotTickSystem dotTickSystem;

    private EntityEffect igniteEffect, coldEffect, infectEffect, corruptEffect;
    private boolean effectsResolved;

    public Nat20ElementalDotSystem(Nat20LootSystem lootSystem, Nat20DotTickSystem dotTickSystem) {
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

        if (!effectsResolved) {
            igniteEffect = EntityEffect.getAssetMap().getAsset(IGNITE_EFFECT);
            coldEffect = EntityEffect.getAssetMap().getAsset(COLD_EFFECT);
            infectEffect = EntityEffect.getAssetMap().getAsset(INFECT_EFFECT);
            corruptEffect = EntityEffect.getAssetMap().getAsset(CORRUPT_EFFECT);
            effectsResolved = true;
            LOGGER.atInfo().log("[ElemDot] resolved effects: ignite=%s cold=%s infect=%s corrupt=%s",
                    igniteEffect != null, coldEffect != null, infectEffect != null, corruptEffect != null);
        }

        UUID attackerUuid = attackerPlayer != null ? attackerPlayer.getPlayerRef().getUuid() : null;
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolledAffix : src.affixes()) {
                String id = rolledAffix.id();
                EntityEffect effect;

                if (IGNITE_ID.equals(id)) {
                    effect = igniteEffect;
                } else if (COLD_ID.equals(id)) {
                    effect = coldEffect;
                } else if (INFECT_ID.equals(id)) {
                    effect = infectEffect;
                } else if (CORRUPT_ID.equals(id)) {
                    effect = corruptEffect;
                } else {
                    continue;
                }

                if (effect == null) {
                    LOGGER.atWarning().log("[ElemDot] effect unavailable for %s", id);
                    continue;
                }

                Nat20AffixDef def = affixRegistry.get(id);
                if (def == null) continue;

                double procChance = parseProcChance(def.procChance());
                if (procChance <= 0) continue;

                PlayerStats stats = attackerPlayer != null ? resolvePlayerStats(attackerRef, store) : null;
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getPowerModifier(primary);
                    procChance *= (1.0 + modifier * def.statScaling().factor());
                }
                procChance = Math.min(procChance, 1.0);

                double roll = ThreadLocalRandom.current().nextDouble();
                if (roll > procChance) continue;

                Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
                EffectControllerComponent effectCtrl =
                        store.getComponent(targetRef, EffectControllerComponent.getComponentType());
                if (effectCtrl == null) continue;

                ThreadLocalRandom rng = ThreadLocalRandom.current();
                // Duration is rolled at item craft time and stored on RolledAffix.
                // Fall back to MAX_DURATION for mob-applied procs that pre-date
                // the durable duration field (duration == 0).
                float duration = rolledAffix.hasDuration()
                        ? (float) rolledAffix.duration()
                        : Nat20DotTickSystem.MAX_DURATION;
                float actualTicks = duration / TICK_INTERVAL;

                com.chonbosmods.loot.def.AffixValueRange range = def.getValuesForRarity(src.rarity());
                float damagePerTick = 1.0f;
                if (range != null) {
                    double rolledLevel = rolledAffix.rollLevel(rng);
                    double perTickAtBase = range.interpolate(rolledLevel, src.ilvl(), src.qualityValue());
                    PlayerStats dotStats = attackerPlayer != null ? resolvePlayerStats(attackerRef, store) : null;
                    if (dotStats != null && def.statScaling() != null) {
                        int mod = dotStats.getPowerModifier(def.statScaling().primary());
                        perTickAtBase *= (1.0 + mod * def.statScaling().factor());
                    }
                    double totalDamage = perTickAtBase * BASE_TICKS;
                    damagePerTick = (float) (totalDamage / actualTicks);
                }

                Nat20DotTickSystem.DotType dotType = switch (id) {
                    case IGNITE_ID -> Nat20DotTickSystem.DotType.IGNITE;
                    case COLD_ID -> Nat20DotTickSystem.DotType.COLD;
                    case INFECT_ID -> Nat20DotTickSystem.DotType.INFECT;
                    case CORRUPT_ID -> Nat20DotTickSystem.DotType.CORRUPT;
                    default -> null;
                };
                boolean isNew = dotType != null
                        && dotTickSystem.registerDot(targetRef, dotType, attackerRef, damagePerTick, duration);

                if (isNew) {
                    Nat20EntityEffectUtil.applyOnce(effectCtrl, targetRef, effect, duration, commandBuffer);
                }

                if (attackerUuid != null && CombatDebugSystem.isEnabled(attackerUuid)) {
                    LOGGER.atInfo().log("[ElemDot] %s new=%s target=%s duration=%.1fs perTick=%.2f",
                            id, isNew, targetRef, duration, damagePerTick);
                }
            }
        }
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
            LOGGER.atWarning().log("[ElemDot] failed to parse proc chance: '%s'", procChanceStr);
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
