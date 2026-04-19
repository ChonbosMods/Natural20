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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves critical hits for player melee attacks in the Filter Group.
 * Scans the attacker's weapon for crit_chance and crit_damage affixes,
 * rolls for a crit, multiplies damage, and swaps DamageCause to Nat20Critical
 * for gold damage text. Same affix-scanning pattern as DeepWounds and AttackSpeed.
 */
public class Nat20CritSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private static final String CRIT_CHANCE_ID = "nat20:crit_chance";
    private static final String CRIT_DAMAGE_ID = "nat20:crit_damage";
    public static final String FORCE_CRIT_ID = "nat20:force_crit";
    private static final double BASE_CRIT_MULTIPLIER = 1.5;
    private static final double SOFTCAP_K_CHANCE = 0.30;
    private static final double SOFTCAP_K_DAMAGE = 2.0;
    private static final double DEX_BASELINE_PER_MOD = 0.015;
    private static final double STR_BASELINE_PER_MOD = 0.15;

    private final Nat20LootSystem lootSystem;
    private int critCauseIdx = -1;
    private boolean causeResolved = false;

    public Nat20CritSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
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

        if (!causeResolved) {
            critCauseIdx = DamageCause.getAssetMap().getIndex("Nat20Critical");
            causeResolved = true;
            if (critCauseIdx < 0) {
                LOGGER.atWarning().log("[Crit] Nat20Critical DamageCause not found");
            }
        }
        if (critCauseIdx < 0) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        UUID playerUuid = attackerPlayer != null ? attackerPlayer.getPlayerRef().getUuid() : null;
        PlayerStats playerStats = attackerPlayer != null ? resolvePlayerStats(attackerRef, store) : null;

        double dexBaseline = playerStats != null
                ? playerStats.getPowerModifier(Stat.DEX) * DEX_BASELINE_PER_MOD : 0;
        double strBaseline = playerStats != null
                ? playerStats.getPowerModifier(Stat.STR) * STR_BASELINE_PER_MOD : 0;

        List<EffectAffixSource.Source> sources = EffectAffixSource.resolveAttackerSources(
                attackerRef, store, lootSystem);
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        boolean forcedCrit = hasAffix(FORCE_CRIT_ID, sources);
        double rawAffixChance = sources.isEmpty() ? 0
                : rawAffixValue(CRIT_CHANCE_ID, sources, affixRegistry);
        double affixChance = Nat20Softcap.softcap(rawAffixChance, SOFTCAP_K_CHANCE);
        double critChance = forcedCrit ? 1.0 : Math.min(1.0, dexBaseline + affixChance);

        if (critChance <= 0) return;
        if (!forcedCrit && ThreadLocalRandom.current().nextDouble() >= critChance) return;

        double rawAffixDamage = sources.isEmpty() ? 0
                : rawAffixValue(CRIT_DAMAGE_ID, sources, affixRegistry);
        double affixDamage = Nat20Softcap.softcap(rawAffixDamage, SOFTCAP_K_DAMAGE);
        double critMultiplier = BASE_CRIT_MULTIPLIER + strBaseline + affixDamage;

        float original = damage.getAmount();
        float critted = (float) (original * critMultiplier);
        damage.setAmount(critted);
        damage.setDamageCauseIndex(critCauseIdx);

        if (playerUuid != null && CombatDebugSystem.isEnabled(playerUuid)) {
            LOGGER.atInfo().log(
                    "[Crit] player=%s dexBase=%.1f%% affix=%.1f%% chance=%.1f%% strBase=%.2f affix=%.2f mult=%.2fx damage=%.1f->%.1f",
                    playerUuid.toString().substring(0, 8),
                    dexBaseline * 100, affixChance * 100, critChance * 100,
                    strBaseline, affixDamage, critMultiplier, original, critted);
        }
    }

    private static boolean hasAffix(String affixId, List<EffectAffixSource.Source> sources) {
        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix affix : src.affixes()) {
                if (affixId.equals(affix.id())) return true;
            }
        }
        return false;
    }

    private static double rawAffixValue(String affixId, List<EffectAffixSource.Source> sources,
                                        Nat20AffixRegistry affixRegistry) {
        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolledAffix : src.affixes()) {
                if (!affixId.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(affixId);
                if (def == null) return 0;

                AffixValueRange range = def.getValuesForRarity(src.rarity());
                if (range == null) return 0;

                return range.interpolate(rolledAffix.midLevel(), src.ilvl(), src.qualityValue());
            }
        }
        return 0;
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
