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

/**
 * Hex: on melee hit, applies a curse on the target. The target's NEXT incoming
 * damage from any source is amplified, then the hex is consumed and the visual removed.
 *
 * Runs in Inspect Group (AFTER Filter Group). Nat20HexConsumeSystem runs in Filter
 * Group. So on a hex-weapon hit:
 *   1. Filter: consume existing hex (amplify this hit)
 *   2. Inspect: apply new hex (for the next hit)
 * This means a hex weapon naturally consumes then reapplies each swing.
 */
public class Nat20HexSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:hex";
    private static final String EFFECT_ID = "Nat20HexEffect";
    private static final double SOFTCAP_K = 1.0;

    private final Nat20LootSystem lootSystem;
    private final Nat20DotTickSystem dotTickSystem;
    private EntityEffect effect;
    private boolean effectResolved;

    // Key: targetRef, Value: hex state
    private final ConcurrentHashMap<Ref<EntityStore>, HexState> hexedTargets = new ConcurrentHashMap<>();

    record HexState(double bonusMultiplier, long expiryMs) {}

    public Nat20HexSystem(Nat20LootSystem lootSystem, Nat20DotTickSystem dotTickSystem) {
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
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolledAffix : src.affixes()) {
                if (!AFFIX_ID.equals(rolledAffix.id())) continue;

                if (!resolveEffect()) return;

                Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);

                Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
                if (def == null) return;

                AffixValueRange range = def.getValuesForRarity(src.rarity());
                if (range == null) return;

                double baseValue = range.interpolate(rolledAffix.midLevel(), src.ilvl(), src.qualityValue());
                double effectiveValue = baseValue;
                PlayerStats stats = attackerPlayer != null ? resolvePlayerStats(attackerRef, store) : null;
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getModifier(primary);
                    effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
                }
                effectiveValue = Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);

                HexState previous = hexedTargets.put(targetRef,
                        new HexState(effectiveValue, System.currentTimeMillis() + 15000));
                if (previous == null || System.currentTimeMillis() > previous.expiryMs) {
                    EffectControllerComponent effectCtrl =
                            store.getComponent(targetRef, EffectControllerComponent.getComponentType());
                    Nat20EntityEffectUtil.applyOnce(effectCtrl, targetRef, effect, commandBuffer);
                }

                if (attackerPlayer != null) {
                    UUID attackerUuid = attackerPlayer.getPlayerRef().getUuid();
                    if (CombatDebugSystem.isEnabled(attackerUuid)) {
                        LOGGER.atInfo().log("[Hex] %s: target=%s bonus=%.1f%%",
                                previous == null ? "applied" : "refreshed",
                                targetRef, effectiveValue * 100);
                    }
                }
                return;
            }
        }
    }

    /**
     * Called by Nat20HexConsumeSystem (Filter Group).
     * Returns and removes hex bonus if target is hexed. Any damage source consumes it.
     */
    public double consumeHex(Ref<EntityStore> targetRef) {
        HexState state = hexedTargets.remove(targetRef);
        if (state == null) return 0;
        if (System.currentTimeMillis() > state.expiryMs) return 0;
        return state.bonusMultiplier;
    }

    /**
     * Check if target has an active hex (for visual cleanup by consume system).
     */
    public boolean hasHex(Ref<EntityStore> targetRef) {
        HexState state = hexedTargets.get(targetRef);
        return state != null && System.currentTimeMillis() <= state.expiryMs;
    }

    public EntityEffect getEffect() {
        resolveEffect();
        return effect;
    }

    public void removePlayer(UUID uuid) {
        // Hex is now target-keyed, not attacker-keyed. No per-player cleanup needed.
    }

    private boolean resolveEffect() {
        if (effectResolved) return effect != null;
        effect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        effectResolved = true;
        if (effect == null) {
            LOGGER.atWarning().log("[Hex] EntityEffect '%s' not found", EFFECT_ID);
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
