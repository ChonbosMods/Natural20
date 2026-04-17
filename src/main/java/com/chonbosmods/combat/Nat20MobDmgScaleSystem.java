package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.progression.MobScalingConfig;
import com.chonbosmods.progression.Nat20MobLevel;
import com.chonbosmods.progression.Tier;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Strike-time outgoing-damage scale for mobs. When the attacker has a
 * {@link Nat20MobLevel}, multiply the dealt damage by tier dmgMult and
 * mlvl dmg growth. Replaces the AttackDamage stat-modifier path, which
 * silently no-ops because AttackDamage is not a registered EntityStatType.
 */
public class Nat20MobDmgScaleSystem extends DamageEventSystem {

    private static final Query<EntityStore> QUERY = Query.any();

    private final MobScalingConfig config;

    public Nat20MobDmgScaleSystem(MobScalingConfig config) {
        this.config = config;
    }

    @Override public Query<EntityStore> getQuery() { return QUERY; }

    @Override public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> cb,
                       Damage damage) {
        if (damage.isCancelled()) return;
        if (!(damage.getSource() instanceof Damage.EntitySource src)) return;

        Ref<EntityStore> attackerRef = src.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Nat20MobLevel level = store.getComponent(attackerRef, Natural20.getMobLevelType());
        if (level == null) return;

        Tier tier = level.getTier();
        int baseMlvl = config.mlvlForTier(level.getAreaLevel(), tier);
        int diffMod = (level.getDifficultyTier() != null)
                ? config.difficultyMlvlMod(level.getDifficultyTier())
                : 0;
        int mlvl = Math.max(1, Math.min(45, baseMlvl + diffMod));
        MobScalingConfig.TierMult mult = config.multipliersFor(tier);

        double scale = config.dmgScale(mlvl) * mult.dmgMult();
        if (scale == 1.0) return;

        damage.setAmount((float) (damage.getAmount() * scale));
    }
}
