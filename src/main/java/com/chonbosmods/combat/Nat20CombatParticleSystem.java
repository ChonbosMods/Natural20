package com.chonbosmods.combat;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class Nat20CombatParticleSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final float TORSO_OFFSET_Y = 0.9f;

    private static final String CAUSE_CRIT = "Nat20Critical";
    private static final String CAUSE_BLEED = "Nat20Bleed";
    private static final String PARTICLE_CRIT = "Nat20_CritMega";
    private static final String PARTICLE_BLEED = "Nat20_BleedSplat";
    private static final String SOUND_CRIT = "SFX_Golem_Earth_Slam_Impact";

    private int critCauseIdx = Integer.MIN_VALUE;
    private int bleedCauseIdx = Integer.MIN_VALUE;
    private int critSoundIdx = Integer.MIN_VALUE;
    private boolean resolved;

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        if (!resolved) {
            critCauseIdx = DamageCause.getAssetMap().getIndex(CAUSE_CRIT);
            bleedCauseIdx = DamageCause.getAssetMap().getIndex(CAUSE_BLEED);
            critSoundIdx = SoundEvent.getAssetMap().getIndex(SOUND_CRIT);
            resolved = true;
            LOGGER.atInfo().log("[CombatParticle] resolved: crit=%d bleed=%d critSound=%d",
                    critCauseIdx, bleedCauseIdx, critSoundIdx);
        }

        int causeIdx = damage.getDamageCauseIndex();
        String particleId;
        boolean isCrit;
        if (causeIdx == critCauseIdx && critCauseIdx >= 0) {
            particleId = PARTICLE_CRIT;
            isCrit = true;
        } else if (causeIdx == bleedCauseIdx && bleedCauseIdx >= 0) {
            particleId = PARTICLE_BLEED;
            isCrit = false;
        } else {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d pos = transform.getPosition();
        double x = pos.getX();
        double y = pos.getY() + TORSO_OFFSET_Y;
        double z = pos.getZ();

        try {
            ParticleUtil.spawnParticleEffect(particleId, new Vector3d(x, y, z), store);
            if (isCrit && critSoundIdx >= 0) {
                SoundUtil.playSoundEvent3d(critSoundIdx, SoundCategory.SFX, x, y, z, store);
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("[CombatParticle] failed: %s", particleId);
        }
    }
}
