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
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS ticking system that applies D&D ability score modifiers as persistent
 * stat bonuses. Only recalculates when a player is marked dirty via
 * {@link Nat20ScoreDirtyFlag}, so the per-tick cost is near zero for
 * unchanged players.
 *
 * <p>Bonus mapping (all scaled by {@link #BONUS_MULTIPLIER}):
 * <ul>
 *   <li>CON modifier -> max Health, max Stamina</li>
 *   <li>INT modifier -> max Mana</li>
 *   <li>DEX modifier -> MovementSpeed</li>
 *   <li>WIS modifier -> perception (written to playerData)</li>
 * </ul>
 */
public class Nat20ScoreBonusSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /** Score bonus multipliers for testing. Tune in Phase 5. */
    private static final float BONUS_MULTIPLIER = 10.0f;
    private static final float STAMINA_MULTIPLIER = 5.0f;

    // Modifier keys: unique per stat source so they don't collide with gear affixes
    private static final String KEY_CON_HEALTH  = "nat20:con_max_health";
    private static final String KEY_CON_STAMINA = "nat20:con_max_stamina";
    private static final String KEY_INT_MANA    = "nat20:int_max_mana";

    private final Query<EntityStore> query;

    /** Cached stat indices, resolved lazily on first dirty tick. */
    private int healthIdx  = -1;
    private int staminaIdx = -1;
    private int manaIdx    = -1;
    private boolean indicesResolved = false;

    public Nat20ScoreBonusSystem() {
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
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID uuid = uuidComp.getUuid();

        if (!Nat20ScoreDirtyFlag.isDirty(uuid)) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        if (playerData == null) {
            Nat20ScoreDirtyFlag.clearDirty(uuid);
            return;
        }

        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            Nat20ScoreDirtyFlag.clearDirty(uuid);
            return;
        }

        resolveIndices();

        PlayerStats stats = PlayerStats.from(playerData);
        int conMod = stats.getModifier(Stat.CON);
        int intMod = stats.getModifier(Stat.INT);
        int wisMod = stats.getModifier(Stat.WIS);

        // CON -> max Health
        applyModifier(statMap, healthIdx, KEY_CON_HEALTH, conMod);

        // CON -> max Stamina (lower multiplier to avoid overwhelming the pool)
        applyModifierScaled(statMap, staminaIdx, KEY_CON_STAMINA, conMod, STAMINA_MULTIPLIER);

        // INT -> max Mana
        applyModifier(statMap, manaIdx, KEY_INT_MANA, intMod);

        // DEX -> movement speed: deferred to Phase 4 (needs MovementManager fallback, no stat type exists)

        // WIS -> perception (stored on playerData, not a stat modifier)
        playerData.setPerception(wisMod * BONUS_MULTIPLIER);

        // Gear affix recomputation: deferred to Phase 4 (needs PlayerScoreChangeEvent)

        Nat20ScoreDirtyFlag.clearDirty(uuid);

        if (CombatDebugSystem.isEnabled(uuid)) {
            LOGGER.atInfo().log("[ScoreBonus] player=%s CON=%+d HP=%+.0f STA=%+.0f INT=%+d MANA=%+.0f WIS=%+d PERC=%.0f",
                    uuid.toString().substring(0, 8),
                    conMod, conMod * BONUS_MULTIPLIER,
                    conMod * STAMINA_MULTIPLIER,
                    intMod, intMod * BONUS_MULTIPLIER,
                    wisMod, wisMod * BONUS_MULTIPLIER);
        }
    }

    /**
     * Apply a score-derived modifier to a stat. Uses putModifier directly (overwrite)
     * instead of remove-then-put, because the remove step causes the engine to clamp
     * the current stat value down to the temporarily reduced max.
     * Only removes when the modifier is zero (no bonus to apply).
     */
    private void applyModifier(EntityStatMap statMap, int statIndex, String key, int modifier) {
        applyModifierScaled(statMap, statIndex, key, modifier, BONUS_MULTIPLIER);
    }

    private void applyModifierScaled(EntityStatMap statMap, int statIndex, String key, int modifier, float multiplier) {
        if (statIndex < 0) return;
        float value = modifier * multiplier;
        if (value == 0) {
            statMap.removeModifier(statIndex, key);
        } else {
            statMap.putModifier(statIndex, key, new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    value
            ));
        }
    }

    /**
     * Resolve EntityStatType indices for the stats we modify. Called once lazily
     * since the asset map is not available at construction time.
     */
    private void resolveIndices() {
        if (indicesResolved) return;
        indicesResolved = true;

        var assetMap = EntityStatType.getAssetMap();
        healthIdx  = assetMap.getIndex("Health");
        staminaIdx = assetMap.getIndex("Stamina");
        manaIdx    = assetMap.getIndex("Mana");

        // Movement speed: no EntityStatType exists in vanilla Hytale.
        // Deferred to Phase 4 alongside Attack Speed (both need MovementManager/AAF).
    }
}
