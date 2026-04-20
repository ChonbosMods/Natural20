package com.chonbosmods.combat;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Enforces the invariant that each Nat20 EntityEffect type has at most one
 * instance per entity at a time.
 *
 * <p>Why: {@link EffectControllerComponent#addEffect} fires {@code EffectOp.Add}
 * to clients even when refreshing an already-active effect (OVERWRITE, EXTEND),
 * which re-triggers {@code ApplicationEffects} client-side: particles and sound
 * re-spawn on top of the existing instance, producing visible stacking.
 *
 * <p>Routing 3-arg {@code addEffect} calls through {@link #applyOnce} gates on
 * {@link EffectControllerComponent#hasEffect(EntityEffect)} so subsequent hits
 * while the effect is active become no-ops. The effect expires on its own
 * duration timer from the first application.
 */
public final class Nat20EntityEffectUtil {

    private Nat20EntityEffectUtil() {}

    public static boolean applyOnce(EffectControllerComponent effectCtrl,
                                    Ref<EntityStore> targetRef,
                                    EntityEffect effect,
                                    ComponentAccessor<EntityStore> accessor) {
        if (effectCtrl == null || effect == null) return false;
        if (effectCtrl.hasEffect(effect)) return false;
        return effectCtrl.addEffect(targetRef, effect, accessor);
    }

    /**
     * Variant that applies the effect with an explicit duration (OVERWRITE) for
     * variable-duration DOTs. Still gated on {@link EffectControllerComponent#hasEffect}
     * so repeat procs while the effect is active are no-ops and don't restart the
     * client-side particle lifespan.
     */
    public static boolean applyOnce(EffectControllerComponent effectCtrl,
                                    Ref<EntityStore> targetRef,
                                    EntityEffect effect,
                                    float duration,
                                    ComponentAccessor<EntityStore> accessor) {
        if (effectCtrl == null || effect == null) return false;
        if (effectCtrl.hasEffect(effect)) return false;
        return effectCtrl.addEffect(targetRef, effect, duration, OverlapBehavior.OVERWRITE, accessor);
    }

}
