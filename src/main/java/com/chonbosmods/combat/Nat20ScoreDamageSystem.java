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
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies D&D ability score bonuses to damage events in the Filter Group:
 * DEX modifier reduces fall damage, STR modifier increases melee damage,
 * INT modifier increases elemental damage (fire/ice/void/poison).
 * Stateless system: no per-player tracking or cleanup needed.
 */
public class Nat20ScoreDamageSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final float BONUS_MULTIPLIER = 10.0f;

    private int fireCauseIdx = Integer.MIN_VALUE;
    private int iceCauseIdx = Integer.MIN_VALUE;
    private int voidCauseIdx = Integer.MIN_VALUE;
    private int poisonCauseIdx = Integer.MIN_VALUE;
    private boolean elementalIndicesResolved = false;
    private final Set<Integer> elementalCauses = ConcurrentHashMap.newKeySet();

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

            // --- INT elemental damage bonus (fire/ice/void/poison) ---
            handleElementalDamage(entitySource, store, damage);
        }
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

        int dexMod = stats.getPowerModifier(Stat.DEX);
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

        int strMod = stats.getPowerModifier(Stat.STR);
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
     * INT elemental bonus: if attacker is a player dealing elemental damage
     * (fire/ice/void/poison), add INT_modifier * BONUS_MULTIPLIER flat damage.
     */
    private void handleElementalDamage(Damage.EntitySource entitySource,
                                       Store<EntityStore> store, Damage damage) {
        resolveElementalIndices();

        DamageCause cause = damage.getCause();
        if (cause == null) return;

        int causeIdx = DamageCause.getAssetMap().getIndex(cause.getId());
        if (!elementalCauses.contains(causeIdx)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        PlayerStats stats = resolvePlayerStats(attackerRef, store);
        if (stats == null) return;

        int intMod = stats.getPowerModifier(Stat.INT);
        if (intMod <= 0) return;

        float bonus = intMod * BONUS_MULTIPLIER;
        float original = damage.getAmount();
        damage.setAmount(original + bonus);

        UUID playerUuid = attackerPlayer.getPlayerRef().getUuid();
        if (CombatDebugSystem.isEnabled(playerUuid)) {
            LOGGER.atInfo().log("[ScoreDamage:INT] player=%s intMod=%d bonus=%.1f damage=%.1f->%.1f cause=%s",
                    playerUuid.toString().substring(0, 8),
                    intMod, bonus, original, original + bonus,
                    cause.getId());
        }
    }

    /**
     * Lazily resolve elemental DamageCause indices from the asset map.
     */
    private void resolveElementalIndices() {
        if (elementalIndicesResolved) return;
        elementalIndicesResolved = true;

        var assetMap = DamageCause.getAssetMap();
        fireCauseIdx = assetMap.getIndex("Nat20Fire");
        iceCauseIdx = assetMap.getIndex("Nat20Ice");
        voidCauseIdx = assetMap.getIndex("Nat20Void");
        poisonCauseIdx = assetMap.getIndex("Nat20Poison");

        if (fireCauseIdx >= 0) elementalCauses.add(fireCauseIdx);
        if (iceCauseIdx >= 0) elementalCauses.add(iceCauseIdx);
        if (voidCauseIdx >= 0) elementalCauses.add(voidCauseIdx);
        if (poisonCauseIdx >= 0) elementalCauses.add(poisonCauseIdx);

        // Also recognize vanilla elemental causes
        int vanillaFire = assetMap.getIndex("Fire");
        int vanillaIce = assetMap.getIndex("Ice");
        int vanillaPoison = assetMap.getIndex("Poison");
        if (vanillaFire >= 0) elementalCauses.add(vanillaFire);
        if (vanillaIce >= 0) elementalCauses.add(vanillaIce);
        if (vanillaPoison >= 0) elementalCauses.add(vanillaPoison);
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
