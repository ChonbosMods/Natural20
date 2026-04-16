package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Random;

/**
 * Authority for monster level/tier scaling. Hooks every entity add; tags any
 * NPCEntity with a {@link Nat20MobLevel} component (default tier REGULAR)
 * and applies HP/damage modifiers. Spawners can call {@link #setTier} to
 * upgrade a mob's tier after spawn (rescale-on-upgrade).
 */
public class Nat20MobScaleSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobScale");
    private static final Query<EntityStore> QUERY = Query.any();

    private static final String MOD_KEY_HP  = "nat20:mob:mlvl-tier-hp";
    private static final String MOD_KEY_DMG = "nat20:mob:mlvl-tier-dmg";

    private final MobScalingConfig config;

    public Nat20MobScaleSystem(MobScalingConfig config) {
        this.config = config;
    }

    @Override public Query<EntityStore> getQuery() { return QUERY; }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason,
                              Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return;

        Nat20MobLevel level = store.getComponent(ref, Natural20.getMobLevelType());
        if (level != null && level.isScaled()) return;  // LOAD of already-scaled mob

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        double x = transform.getPosition().getX();
        double z = transform.getPosition().getZ();
        double distance = Math.sqrt(x * x + z * z);
        int areaLevel = config.areaLevelForDistance(distance);

        if (level == null) {
            level = new Nat20MobLevel();
            store.putComponent(ref, Natural20.getMobLevelType(), level);
        }
        level.setAreaLevel(areaLevel);
        level.setTier(Tier.REGULAR);
        applyStats(ref, store, level);
        level.setScaled(true);

        LOGGER.atInfo().log("Tagged %s areaLevel=%d tier=REGULAR (distance=%.1f)",
                npc.getRoleName(), areaLevel, distance);
    }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason,
                               Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        // Nothing to clean up: Nat20MobLevel is codec-persisted and the entity's gone.
    }

    /** Upgrade a mob's tier after spawn. Called by spawners for CHAMPION/BOSS groups. */
    public void setTier(Ref<EntityStore> ref, Store<EntityStore> store, Tier newTier) {
        Nat20MobLevel level = store.getComponent(ref, Natural20.getMobLevelType());
        if (level == null) {
            LOGGER.atWarning().log("setTier called on untagged ref=%s; skipping", ref);
            return;
        }
        level.setTier(newTier);
        applyStats(ref, store, level);
        LOGGER.atInfo().log("Retier ref=%s areaLevel=%d tier=%s",
                ref, level.getAreaLevel(), newTier);
    }

    /**
     * Weighted-random DifficultyTier roll using default weights from config
     * (UNCOMMON / RARE / EPIC only; LEGENDARY is boss-conditional, not from this roll).
     * Returns UNCOMMON if weights sum to 0 (defensive).
     */
    public DifficultyTier rollDifficultyWeighted(Random rng) {
        DifficultyTier[] pool = { DifficultyTier.UNCOMMON, DifficultyTier.RARE, DifficultyTier.EPIC };
        int total = 0;
        for (DifficultyTier d : pool) total += config.difficultyWeight(d);
        if (total <= 0) return DifficultyTier.UNCOMMON;
        int roll = rng.nextInt(total);
        int cum = 0;
        for (DifficultyTier d : pool) {
            cum += config.difficultyWeight(d);
            if (roll < cum) return d;
        }
        return DifficultyTier.EPIC;
    }

    /**
     * Write the DifficultyTier onto the mob's Nat20MobLevel, re-apply stats (so mlvl
     * modifier takes effect), and attach the persistent tint effect. Idempotent:
     * re-calling with the same DifficultyTier is safe; calling with a different
     * tier overwrites tint + mlvl mod.
     */
    public void applyDifficulty(Ref<EntityStore> ref, Store<EntityStore> store,
                                CommandBuffer<EntityStore> cb, DifficultyTier difficulty) {
        Nat20MobLevel level = store.getComponent(ref, Natural20.getMobLevelType());
        if (level == null) {
            LOGGER.atWarning().log("applyDifficulty on untagged ref=%s; skipping", ref);
            return;
        }
        level.setDifficultyTier(difficulty);
        applyStats(ref, store, level);
        applyTint(ref, store, cb, difficulty);
        LOGGER.atInfo().log("Applied difficulty ref=%s tier=%s areaLevel=%d",
                ref, difficulty, level.getAreaLevel());
    }

    private void applyTint(Ref<EntityStore> ref, Store<EntityStore> store,
                           CommandBuffer<EntityStore> cb, DifficultyTier difficulty) {
        try {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(difficulty.tintEffectId());
            if (effect == null) {
                LOGGER.atWarning().log("Missing EntityEffect asset '%s'", difficulty.tintEffectId());
                return;
            }
            EffectControllerComponent controller =
                    store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (controller == null) {
                LOGGER.atWarning().log("No EffectControllerComponent on ref=%s", ref);
                return;
            }
            controller.addEffect(ref, effect, cb);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to apply tint effect for %s", difficulty);
        }
    }

    private void applyStats(Ref<EntityStore> ref, Store<EntityStore> store, Nat20MobLevel level) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int baseMlvl = config.mlvlForTier(level.getAreaLevel(), level.getTier());
        int diffMod = (level.getDifficultyTier() != null)
                ? config.difficultyMlvlMod(level.getDifficultyTier())
                : 0;
        int mlvl = Math.max(1, Math.min(45, baseMlvl + diffMod));
        MobScalingConfig.TierMult mult = config.multipliersFor(level.getTier());

        double hpMult  = config.hpScale(mlvl)  * mult.hpMult();
        double dmgMult = config.dmgScale(mlvl) * mult.dmgMult();

        int hpStat  = EntityStatType.getAssetMap().getIndex("Health");
        int dmgStat = EntityStatType.getAssetMap().getIndex("AttackDamage");

        statMap.putModifier(hpStat,  MOD_KEY_HP,
                new StaticModifier(Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.MULTIPLICATIVE, (float) hpMult));
        statMap.putModifier(dmgStat, MOD_KEY_DMG,
                new StaticModifier(Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.MULTIPLICATIVE, (float) dmgMult));
    }
}
