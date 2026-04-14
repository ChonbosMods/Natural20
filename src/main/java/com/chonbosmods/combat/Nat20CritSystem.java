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
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
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

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        if (!causeResolved) {
            critCauseIdx = DamageCause.getAssetMap().getIndex("Nat20Critical");
            causeResolved = true;
            if (critCauseIdx < 0) {
                LOGGER.atWarning().log("[Crit] Nat20Critical DamageCause not found");
            }
        }
        if (critCauseIdx < 0) return;

        ItemStack weapon = InventoryComponent.getItemInHand(store, attackerRef);
        if (weapon == null || weapon.isEmpty()) return;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        PlayerStats playerStats = resolvePlayerStats(attackerRef, store);
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        UUID playerUuid = attackerPlayer.getPlayerRef().getUuid();

        // Dev-only guaranteed-crit marker: /nat20 testcritweapon applies this affix ID
        // so the tester can verify the crit visual/sound pipeline without RNG.
        boolean forcedCrit = hasAffix(FORCE_CRIT_ID, lootData);
        double critChance;
        if (forcedCrit) {
            critChance = 1.0;
        } else {
            critChance = computeAffixValue(CRIT_CHANCE_ID, lootData, affixRegistry, playerStats, SOFTCAP_K_CHANCE);
            if (critChance <= 0) return;
        }

        if (CombatDebugSystem.isEnabled(playerUuid)) {
            double critDmgBonus = computeAffixValue(CRIT_DAMAGE_ID, lootData, affixRegistry, playerStats, SOFTCAP_K_DAMAGE);
            LOGGER.atInfo().log("[Crit:check] player=%s chance=%.1f%% dmgBonus=%.2f forced=%s",
                    playerUuid.toString().substring(0, 8),
                    critChance * 100, critDmgBonus, forcedCrit);
        }

        // Roll (skipped for forced crits)
        if (!forcedCrit && ThreadLocalRandom.current().nextDouble() >= critChance) return;

        // Crit! Compute damage multiplier
        double critDmgBonus = computeAffixValue(CRIT_DAMAGE_ID, lootData, affixRegistry, playerStats, SOFTCAP_K_DAMAGE);
        double critMultiplier = BASE_CRIT_MULTIPLIER + critDmgBonus;

        float original = damage.getAmount();
        float critted = (float) (original * critMultiplier);
        damage.setAmount(critted);
        damage.setDamageCauseIndex(critCauseIdx);

        if (CombatDebugSystem.isEnabled(playerUuid)) {
            LOGGER.atInfo().log("[Crit] player=%s chance=%.1f%% mult=%.2fx damage=%.1f->%.1f",
                    playerUuid.toString().substring(0, 8),
                    critChance * 100, critMultiplier, original, critted);
        }
    }

    private static boolean hasAffix(String affixId, Nat20LootData lootData) {
        for (RolledAffix affix : lootData.getAffixes()) {
            if (affixId.equals(affix.id())) return true;
        }
        return false;
    }

    private double computeAffixValue(String affixId, Nat20LootData lootData,
                                      Nat20AffixRegistry affixRegistry,
                                      @Nullable PlayerStats playerStats, double softcapK) {
        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!affixId.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(affixId);
            if (def == null) return 0;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return 0;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;

            if (playerStats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = playerStats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }

            return Nat20Softcap.softcap(effectiveValue, softcapK);
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
