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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vicious Mockery: on melee hit, applies debuff EntityEffect on target.
 * A companion Filter Group system amplifies incoming damage when the effect is active.
 * Uses per-target state map to track the amplification multiplier.
 */
public class Nat20ViciousMockerySystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:vicious_mockery";
    private static final String EFFECT_ID = "Nat20ViciousMockeryEffect";
    private static final double SOFTCAP_K = 0.50;

    private final Nat20LootSystem lootSystem;
    private EntityEffect effect;
    private boolean effectResolved;

    // Tracks active mockery multiplier per target entity
    private final ConcurrentHashMap<Ref<EntityStore>, ViciousState> activeTargets = new ConcurrentHashMap<>();

    record ViciousState(double amplifyMultiplier, long expiryMs) {}

    public Nat20ViciousMockerySystem(Nat20LootSystem lootSystem) {
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
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolledAffix : src.affixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                if (!resolveEffect()) return;

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) return;

                PlayerStats stats = attackerPlayer != null ? resolvePlayerStats(attackerRef, store) : null;

                // Proc chance gate: base from ProcChance, scaled by the stat declared
                // in ProcScaling (typically WIS). Kept distinct from StatScaling which
                // scales the amplify value (typically CHA).
                double procChance = parseProcChance(def.procChance());
                if (procChance <= 0) return;
                if (stats != null && def.procScaling() != null) {
                    Stat primary = def.procScaling().primary();
                    int modifier = stats.getPowerModifier(primary);
                    procChance *= (1.0 + modifier * def.procScaling().factor());
                }
                procChance = Math.min(procChance, 1.0);
                if (ThreadLocalRandom.current().nextDouble() > procChance) return;

                AffixValueRange range = def.getValuesForRarity(src.rarity());
                if (range == null) return;

                double baseValue = range.interpolate(rolledAffix.midLevel(), src.ilvl(), src.qualityValue());
                double effectiveValue = baseValue;
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getPowerModifier(primary);
                    effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
                }
                effectiveValue = Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);

                Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
                EffectControllerComponent effectCtrl =
                        store.getComponent(targetRef, EffectControllerComponent.getComponentType());
                if (effectCtrl == null) return;

                ViciousState previous = activeTargets.put(targetRef,
                        new ViciousState(effectiveValue, System.currentTimeMillis() + 8000));
                boolean isNew = previous == null || System.currentTimeMillis() > previous.expiryMs;
                if (isNew) {
                    Nat20EntityEffectUtil.applyOnce(effectCtrl, targetRef, effect, commandBuffer);
                }

                if (attackerPlayer != null) {
                    UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
                    if (CombatDebugSystem.isEnabled(attackerUuid)) {
                        LOGGER.atInfo().log("[ViciousMockery] %s: target=%s amplify=%.1f%%",
                                isNew ? "applied" : "refreshed", targetRef, effectiveValue * 100);
                    }
                }
                return;
            }
        }
    }

    /**
     * Called by the companion Filter Group amplification system.
     * Returns the damage multiplier if target has active mockery, 0 otherwise.
     */
    public double getAmplifyMultiplier(Ref<EntityStore> targetRef) {
        ViciousState state = activeTargets.get(targetRef);
        if (state == null) return 0;
        if (System.currentTimeMillis() > state.expiryMs) {
            activeTargets.remove(targetRef);
            return 0;
        }
        return state.amplifyMultiplier;
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
            LOGGER.atWarning().log("[ViciousMockery] failed to parse proc chance: '%s'", procChanceStr);
            return 0.0;
        }
    }

    private boolean resolveEffect() {
        if (effectResolved) return effect != null;
        effect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        effectResolved = true;
        if (effect == null) {
            LOGGER.atWarning().log("[ViciousMockery] EntityEffect '%s' not found", EFFECT_ID);
        }
        return effect != null;
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
