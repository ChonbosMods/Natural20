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

/**
 * Elemental + Physical Resistance: Filter Group system.
 * On incoming damage to player, scans armor for matching resistance affixes
 * and reduces damage by the effective percentage. Handles all 5 resistance types.
 */
public class Nat20ResistanceSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final double SOFTCAP_K = 0.50;

    private static final String FIRE_RESIST_ID = "nat20:fire_resistance";
    private static final String FROST_RESIST_ID = "nat20:frost_resistance";
    private static final String VOID_RESIST_ID = "nat20:void_resistance";
    private static final String POISON_RESIST_ID = "nat20:poison_resistance";
    private static final String PHYSICAL_RESIST_ID = "nat20:physical_resistance";

    private final Nat20LootSystem lootSystem;

    private int fireCauseIdx = Integer.MIN_VALUE;
    private int iceCauseIdx = Integer.MIN_VALUE;
    private int poisonCauseIdx = Integer.MIN_VALUE;
    private int voidCauseIdx = Integer.MIN_VALUE;
    private int physicalCauseIdx = Integer.MIN_VALUE;
    // Vanilla elemental causes
    private int vanillaFireIdx = Integer.MIN_VALUE;
    private int vanillaIceIdx = Integer.MIN_VALUE;
    private int vanillaPoisonIdx = Integer.MIN_VALUE;
    private boolean causesResolved;

    public Nat20ResistanceSystem(Nat20LootSystem lootSystem) {
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
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);

        if (!causesResolved) {
            var assetMap = DamageCause.getAssetMap();
            fireCauseIdx = assetMap.getIndex("Nat20Fire");
            iceCauseIdx = assetMap.getIndex("Nat20Ice");
            poisonCauseIdx = assetMap.getIndex("Nat20Poison");
            voidCauseIdx = assetMap.getIndex("Nat20Void");
            physicalCauseIdx = assetMap.getIndex("Physical");
            vanillaFireIdx = assetMap.getIndex("Fire");
            vanillaIceIdx = assetMap.getIndex("Ice");
            vanillaPoisonIdx = assetMap.getIndex("Poison");
            causesResolved = true;
        }

        int causeIdx = damage.getDamageCauseIndex();
        String resistAffixId;

        if ((causeIdx == fireCauseIdx && fireCauseIdx >= 0)
                || (causeIdx == vanillaFireIdx && vanillaFireIdx >= 0)) {
            resistAffixId = FIRE_RESIST_ID;
        } else if ((causeIdx == iceCauseIdx && iceCauseIdx >= 0)
                || (causeIdx == vanillaIceIdx && vanillaIceIdx >= 0)) {
            resistAffixId = FROST_RESIST_ID;
        } else if ((causeIdx == poisonCauseIdx && poisonCauseIdx >= 0)
                || (causeIdx == vanillaPoisonIdx && vanillaPoisonIdx >= 0)) {
            resistAffixId = POISON_RESIST_ID;
        } else if (causeIdx == voidCauseIdx && voidCauseIdx >= 0) {
            resistAffixId = VOID_RESIST_ID;
        } else if (causeIdx == physicalCauseIdx && physicalCauseIdx >= 0) {
            resistAffixId = PHYSICAL_RESIST_ID;
        } else {
            return;
        }

        List<EffectAffixSource.Source> sources = EffectAffixSource.resolveDefenderSources(
                targetRef, store, lootSystem);
        if (sources.isEmpty()) return;

        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        PlayerStats stats = targetPlayer != null ? resolvePlayerStats(targetRef, store) : null;
        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        double totalResistance = 0;

        for (EffectAffixSource.Source src : sources) {
            for (RolledAffix rolledAffix : src.affixes()) {
                if (!resistAffixId.equals(rolledAffix.id())) continue;

                Nat20AffixDef def = affixRegistry.get(resistAffixId);
                if (def == null) continue;

                AffixValueRange range = def.getValuesForRarity(src.rarity());
                if (range == null) continue;

                double baseValue = range.interpolate(rolledAffix.midLevel(), src.ilvl(), src.qualityValue());
                double effectiveValue = baseValue;
                if (stats != null && def.statScaling() != null) {
                    Stat primary = def.statScaling().primary();
                    int modifier = stats.getModifier(primary);
                    effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
                }
                totalResistance += effectiveValue;
            }
        }

        if (totalResistance <= 0) return;

        totalResistance = Nat20Softcap.softcap(totalResistance, SOFTCAP_K);

        float original = damage.getAmount();
        float reduced = (float) (original * (1.0 - totalResistance));
        if (reduced < 0f) reduced = 0f;
        damage.setAmount(reduced);

        if (targetPlayer != null) {
            UUID targetUuid = targetPlayer.getPlayerRef().getUuid();
            if (CombatDebugSystem.isEnabled(targetUuid)) {
                LOGGER.atInfo().log("[Resistance] %s: resist=%.1f%% damage=%.1f->%.1f",
                        resistAffixId, totalResistance * 100, original, reduced);
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
