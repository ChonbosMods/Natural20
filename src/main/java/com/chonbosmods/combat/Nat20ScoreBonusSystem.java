package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
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

    /** All score bonuses are multiplied by this for testing visibility. */
    private static final float BONUS_MULTIPLIER = 10.0f;

    // Modifier keys: unique per stat source so they don't collide with gear affixes
    private static final String KEY_CON_HEALTH  = "nat20:con_max_health";
    private static final String KEY_CON_STAMINA = "nat20:con_max_stamina";
    private static final String KEY_INT_MANA    = "nat20:int_max_mana";
    private static final String KEY_DEX_SPEED   = "nat20:dex_move_speed";

    private final Query<EntityStore> query;

    /** Cached stat indices, resolved lazily on first dirty tick. */
    private int healthIdx  = -1;
    private int staminaIdx = -1;
    private int manaIdx    = -1;
    private int speedIdx   = -1;
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
        int dexMod = stats.getModifier(Stat.DEX);
        int wisMod = stats.getModifier(Stat.WIS);

        // CON -> max Health
        applyModifier(statMap, healthIdx, KEY_CON_HEALTH, conMod);

        // CON -> max Stamina
        applyModifier(statMap, staminaIdx, KEY_CON_STAMINA, conMod);

        // INT -> max Mana
        applyModifier(statMap, manaIdx, KEY_INT_MANA, intMod);

        // DEX -> movement speed
        if (speedIdx >= 0) {
            applyModifier(statMap, speedIdx, KEY_DEX_SPEED, dexMod);
        } else {
            LOGGER.atWarning().log("[ScoreBonus] MovementSpeed stat not found: "
                    + "tried 'MovementSpeed' and 'hytale:movement_speed'");
        }

        // WIS -> perception (stored on playerData, not a stat modifier)
        playerData.setPerception(wisMod * BONUS_MULTIPLIER);

        // Trigger gear affix recomputation when scores change
        recomputeGearAffixes(uuid);

        Nat20ScoreDirtyFlag.clearDirty(uuid);

        if (CombatDebugSystem.isEnabled(uuid)) {
            LOGGER.atInfo().log("[ScoreBonus] player=%s CON=%+d HP/STA=%+.0f INT=%+d MANA=%+.0f "
                            + "DEX=%+d SPD=%+.0f WIS=%+d PERC=%.0f",
                    uuid.toString().substring(0, 8),
                    conMod, conMod * BONUS_MULTIPLIER,
                    intMod, intMod * BONUS_MULTIPLIER,
                    dexMod, dexMod * BONUS_MULTIPLIER,
                    wisMod, wisMod * BONUS_MULTIPLIER);
        }
    }

    /**
     * Remove the old modifier (if any) and apply a new one for the given stat index.
     */
    private void applyModifier(EntityStatMap statMap, int statIndex, String key, int modifier) {
        if (statIndex < 0) return;
        statMap.removeModifier(statIndex, key);
        float value = modifier * BONUS_MULTIPLIER;
        StaticModifier mod = new StaticModifier(
                Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,
                value
        );
        statMap.putModifier(statIndex, key, mod);
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

        // Try both possible names for movement speed
        speedIdx = assetMap.getIndex("MovementSpeed");
        if (speedIdx == AssetMapWithIndexes.NOT_FOUND) {
            speedIdx = assetMap.getIndex("hytale:movement_speed");
        }
    }

    /**
     * Recompute gear affix modifiers after a score change. Score-based scaling
     * on gear affixes means the effective values change when ability scores do.
     *
     * <p>Stub: will be wired in Phase 4 when PlayerScoreChangeEvent is available
     * to trigger full equipment re-evaluation through Nat20EquipmentListener.
     */
    private void recomputeGearAffixes(UUID uuid) {
        // No-op: Phase 4 will fire PlayerScoreChangeEvent here, which
        // Nat20EquipmentListener will handle to strip and reapply all
        // gear modifiers with updated PlayerStats.
    }
}
