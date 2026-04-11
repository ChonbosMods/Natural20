package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
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
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Applies D&D ability score bonuses to damage events in the Filter Group:
 * DEX modifier reduces fall damage, STR modifier increases melee damage.
 * Stateless system: no per-player tracking or cleanup needed.
 */
public class Nat20ScoreDamageSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final float BONUS_MULTIPLIER = 10.0f;

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

        // --- DEX fall damage reduction ---
        String causeId = damage.getCause() != null ? damage.getCause().getId() : "";
        if (causeId.toLowerCase().contains("fall")) {
            handleFallDamage(entityIndex, chunk, store, damage);
            return;
        }

        // --- STR melee damage bonus ---
        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            handleMeleeDamage(entitySource, store, damage);
        }

        // --- INT magical bonus: stub for Phase 4 ---
    }

    /**
     * DEX fall reduction: if target is a player, reduce fall damage by DEX_modifier * BONUS_MULTIPLIER,
     * floored at 0.
     */
    private void handleFallDamage(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                                  Store<EntityStore> store, Damage damage) {
        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer == null) return;

        PlayerStats stats = resolvePlayerStats(targetRef, store);
        if (stats == null) return;

        int dexMod = stats.getModifier(Stat.DEX);
        if (dexMod <= 0) return;

        float reduction = dexMod * BONUS_MULTIPLIER;
        float original = damage.getAmount();
        float reduced = Math.max(0.0f, original - reduction);
        damage.setAmount(reduced);

        UUID playerUuid = targetPlayer.getPlayerRef().getUuid();
        if (CombatDebugSystem.isEnabled(playerUuid)) {
            LOGGER.atInfo().log("[ScoreDamage:DEX] player=%s dexMod=%d reduction=%.1f fall=%.1f->%.1f",
                    playerUuid.toString().substring(0, 8),
                    dexMod, reduction, original, reduced);
        }
    }

    /**
     * STR melee bonus: if attacker is a player with positive STR modifier,
     * add STR_modifier * BONUS_MULTIPLIER flat damage.
     */
    private void handleMeleeDamage(Damage.EntitySource entitySource, Store<EntityStore> store,
                                   Damage damage) {
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        PlayerStats stats = resolvePlayerStats(attackerRef, store);
        if (stats == null) return;

        int strMod = stats.getModifier(Stat.STR);
        if (strMod <= 0) return;

        float bonus = strMod * BONUS_MULTIPLIER;
        float original = damage.getAmount();
        damage.setAmount(original + bonus);

        UUID playerUuid = attackerPlayer.getPlayerRef().getUuid();
        if (CombatDebugSystem.isEnabled(playerUuid)) {
            LOGGER.atInfo().log("[ScoreDamage:STR] player=%s strMod=%d bonus=%.1f damage=%.1f->%.1f",
                    playerUuid.toString().substring(0, 8),
                    strMod, bonus, original, original + bonus);
        }
    }

    /**
     * Resolve the player's D&D stats, or null if unavailable.
     */
    private PlayerStats resolvePlayerStats(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            return playerData != null ? PlayerStats.from(playerData) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
