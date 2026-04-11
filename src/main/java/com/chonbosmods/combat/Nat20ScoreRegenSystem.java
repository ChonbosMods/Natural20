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
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Score-based regen system: ECS ticking system that boosts stamina and mana
 * natural regeneration based on STR and WIS modifiers respectively.
 *
 * <p>Each tick, detects when stamina or mana increased (natural regen) and
 * multiplies the delta by the corresponding stat modifier scaled by a factor.
 * STR modifier scales stamina regen, WIS modifier scales mana regen.
 */
public class Nat20ScoreRegenSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /** STR modifier multiplied by this factor gives the stamina regen bonus ratio. */
    private static final double STR_STAMINA_FACTOR = 0.18;

    /** WIS modifier multiplied by this factor gives the mana regen bonus ratio. */
    private static final double WIS_MANA_FACTOR = 0.025;

    /** Log at most once per second (20 ticks) to avoid spamming. */
    private static final int LOG_INTERVAL_TICKS = 20;

    private final Query<EntityStore> query;
    private final ConcurrentHashMap<UUID, RegenState> playerStates = new ConcurrentHashMap<>();

    public Nat20ScoreRegenSystem() {
        this.query = Query.and(new Query[]{
                Query.any(), UUIDComponent.getComponentType(), Player.getComponentType()
        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (dt <= 0.0f) return;

        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID playerUuid = uuidComp.getUuid();

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int staminaIdx = EntityStatType.getAssetMap().getIndex("Stamina");
        int manaIdx = EntityStatType.getAssetMap().getIndex("Mana");
        if (staminaIdx < 0 || manaIdx < 0) return;

        float currentStamina = statMap.get(staminaIdx).get();
        float currentMana = statMap.get(manaIdx).get();

        RegenState prev = playerStates.get(playerUuid);
        if (prev == null) {
            playerStates.put(playerUuid, new RegenState(currentStamina, currentMana));
            return;
        }

        // Resolve player stats for modifiers
        Nat20PlayerData playerData = null;
        PlayerStats stats = null;
        try {
            playerData = store.getComponent(ref, Natural20.getPlayerDataType());
            if (playerData != null) {
                stats = PlayerStats.from(playerData);
            }
        } catch (Exception e) {
            // No player data yet: skip
        }

        boolean boostedStamina = false;
        boolean boostedMana = false;
        int strMod = 0;
        int wisMod = 0;

        if (stats != null) {
            strMod = stats.getModifier(Stat.STR);
            wisMod = stats.getModifier(Stat.WIS);

            // Stamina regen boost from STR
            float staminaDelta = currentStamina - prev.lastStamina;
            if (staminaDelta > 0 && strMod > 0) {
                float boost = (float) (staminaDelta * strMod * STR_STAMINA_FACTOR);
                float maxStamina = statMap.get(staminaIdx).getMax();
                float newStamina = Math.min(currentStamina + boost, maxStamina);
                boost = newStamina - currentStamina;

                if (boost > 0) {
                    statMap.addStatValue(staminaIdx, boost);
                    boostedStamina = true;
                }
            }

            // Mana regen boost from WIS
            float manaDelta = currentMana - prev.lastMana;
            if (manaDelta > 0 && wisMod > 0) {
                float boost = (float) (manaDelta * wisMod * WIS_MANA_FACTOR);
                float maxMana = statMap.get(manaIdx).getMax();
                float newMana = Math.min(currentMana + boost, maxMana);
                boost = newMana - currentMana;

                if (boost > 0) {
                    statMap.addStatValue(manaIdx, boost);
                    boostedMana = true;
                }
            }
        }

        // Re-read final values after potential boosts for next tick's comparison
        prev.lastStamina = statMap.get(staminaIdx).get();
        prev.lastMana = statMap.get(manaIdx).get();

        // Debug logging once per second
        if (boostedStamina || boostedMana) {
            prev.ticksSinceLog++;
            if (prev.ticksSinceLog >= LOG_INTERVAL_TICKS && CombatDebugSystem.isEnabled(playerUuid)) {
                LOGGER.atInfo().log("[ScoreRegen] player=%s STR=%d(+%.0f%% sta) WIS=%d(+%.0f%% mana) sta=%.0f/%.0f mana=%.0f/%.0f",
                        playerUuid.toString().substring(0, 8),
                        strMod, strMod * STR_STAMINA_FACTOR * 100.0,
                        wisMod, wisMod * WIS_MANA_FACTOR * 100.0,
                        prev.lastStamina, statMap.get(staminaIdx).getMax(),
                        prev.lastMana, statMap.get(manaIdx).getMax());
                prev.ticksSinceLog = 0;
            }
        } else {
            prev.ticksSinceLog = 0;
        }
    }

    /** Clean up state on disconnect. */
    public void removePlayer(UUID uuid) {
        playerStates.remove(uuid);
    }

    private static class RegenState {
        float lastStamina;
        float lastMana;
        int ticksSinceLog;

        RegenState(float stamina, float mana) {
            this.lastStamina = stamina;
            this.lastMana = mana;
        }
    }
}
