package com.chonbosmods.combat;

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
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves critical hits for player melee attacks in the Filter Group.
 * Reads Nat20CritChance and Nat20CritDamage stat values (set by StaticModifier
 * from equipped crit affixes) and rolls for a crit on each hit. On crit,
 * multiplies damage and swaps DamageCause to Nat20Critical for gold text.
 * Stateless system: no per-player tracking or cleanup needed.
 */
public class Nat20CritSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    // Lazily resolved indices (asset maps not ready during construction)
    private int critChanceIdx = -1;
    private int critDamageIdx = -1;
    private int critCauseIdx = -1;
    private boolean indicesResolved = false;

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

        // Only process melee hits from entities
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Only process player attacks
        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) return;

        // Lazily resolve asset indices
        if (!indicesResolved) {
            resolveIndices();
            if (!indicesResolved) return;
        }

        // Read crit stats from attacker's EntityStatMap
        EntityStatMap statMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        float critChance = statMap.get(critChanceIdx).get();
        if (critChance <= 0.0f) return;

        // Roll for crit
        if (ThreadLocalRandom.current().nextFloat() >= critChance) return;

        // Crit hit: read damage multiplier, floor at 1.0
        float critDamageMultiplier = statMap.get(critDamageIdx).get();
        if (critDamageMultiplier < 1.0f) critDamageMultiplier = 1.0f;

        float originalDamage = damage.getAmount();
        float critDamage = originalDamage * critDamageMultiplier;
        damage.setAmount(critDamage);

        // Swap DamageCause for gold text
        damage.setDamageCauseIndex(critCauseIdx);

        // Debug logging
        UUID playerUuid = attackerPlayer.getPlayerRef().getUuid();
        if (CombatDebugSystem.isEnabled(playerUuid)) {
            LOGGER.atInfo().log("[Crit] player=%s chance=%.2f multiplier=%.2f damage=%.1f->%.1f",
                    playerUuid.toString().substring(0, 8),
                    critChance, critDamageMultiplier,
                    originalDamage, critDamage);
        }
    }

    /**
     * Lazily resolve stat and DamageCause indices from asset maps.
     */
    private void resolveIndices() {
        critChanceIdx = EntityStatType.getAssetMap().getIndex("Nat20CritChance");
        critDamageIdx = EntityStatType.getAssetMap().getIndex("Nat20CritDamage");
        critCauseIdx = DamageCause.getAssetMap().getIndex("Nat20Critical");

        if (critChanceIdx >= 0 && critDamageIdx >= 0 && critCauseIdx >= 0) {
            indicesResolved = true;
        } else {
            LOGGER.atWarning().log("[Crit] Failed to resolve indices: critChance=%d critDamage=%d critCause=%d",
                    critChanceIdx, critDamageIdx, critCauseIdx);
        }
    }
}
