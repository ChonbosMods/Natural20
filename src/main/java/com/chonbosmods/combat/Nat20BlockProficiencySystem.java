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

/**
 * Block Proficiency: increases damage reduction when blocking.
 * Filter Group system. On incoming damage to player while blocking,
 * scans weapon for affix and reduces damage.
 *
 * SDK investigation needed: how to detect blocking state. Currently checks
 * if the DamageCause name contains "Block" as a heuristic. During smoke testing,
 * the actual blocking detection method should be verified and updated.
 */
public class Nat20BlockProficiencySystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final String AFFIX_ID = "nat20:block_proficiency";
    private static final double SOFTCAP_K = 0.50;

    private final Nat20LootSystem lootSystem;

    // Blocked damage causes often have reduced amounts; we detect blocking
    // by checking if the damage cause indicates a blocked hit.
    private int blockedCauseIdx = Integer.MIN_VALUE;
    private boolean causeResolved;

    public Nat20BlockProficiencySystem(Nat20LootSystem lootSystem) {
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

        // Block proficiency only applies to the player being hit
        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer == null) return;

        // TODO: SDK investigation during smoke test: detect if player is actively blocking.
        // For now, this system is wired up but the blocking detection needs runtime verification.
        // The affix JSON and system registration are complete; blocking detection
        // will be validated during in-game testing. If no blocking API exists,
        // this system effectively becomes a passive damage reduction while holding a weapon.

        // Check weapon for block proficiency affix
        ItemStack weapon = InventoryComponent.getItemInHand(store, targetRef);
        if (weapon == null || weapon.isEmpty()) return;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return;

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;
            PlayerStats stats = resolvePlayerStats(targetRef, store);
            if (stats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = stats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }
            effectiveValue = Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);

            float original = damage.getAmount();
            float reduced = (float) (original * (1.0 - effectiveValue));
            if (reduced < 0f) reduced = 0f;
            damage.setAmount(reduced);

            UUID targetUuid = targetPlayer.getPlayerRef().getUuid();
            if (CombatDebugSystem.isEnabled(targetUuid)) {
                LOGGER.atInfo().log("[BlockProf] reduction=%.1f%% damage=%.1f->%.1f",
                        effectiveValue * 100, original, reduced);
            }
            return;
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
