package com.chonbosmods.combat;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Filter Group companion for Hex: on any incoming damage to a hexed target,
 * consumes the hex for bonus damage and removes the visual EntityEffect.
 *
 * Filter Group runs BEFORE Inspect Group, so if the attacker also has a hex weapon,
 * the old hex is consumed here first, then a new hex is applied by Nat20HexSystem.
 */
public class Nat20HexConsumeSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20HexSystem hexSystem;
    private final Nat20DotTickSystem dotTickSystem;

    public Nat20HexConsumeSystem(Nat20HexSystem hexSystem, Nat20DotTickSystem dotTickSystem) {
        this.hexSystem = hexSystem;
        this.dotTickSystem = dotTickSystem;
    }

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
        if (damage.isCancelled() || damage.getAmount() <= 0f) return;

        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        double bonus = hexSystem.consumeHex(targetRef);
        if (bonus <= 0) return;

        // Amplify damage
        float original = damage.getAmount();
        float hexed = (float) (original * (1.0 + bonus));
        damage.setAmount(hexed);

        // Overwrite hex effect with 0.1s duration to kill tint fast.
        // Particle spawner stops emitting when the short effect expires;
        // existing particles fade within their ParticleLifeSpan (~0.8s).
        EntityEffect hexEffect = hexSystem.getEffect();
        if (hexEffect != null) {
            EffectControllerComponent effectCtrl =
                    store.getComponent(targetRef, EffectControllerComponent.getComponentType());
            if (effectCtrl != null) {
                effectCtrl.addEffect(targetRef, hexEffect, 0.1f,
                        com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior.OVERWRITE,
                        commandBuffer);
            }
        }

        // Log with attacker info if available
        Damage.Source source = damage.getSource();
        String attackerInfo = "unknown";
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef != null && attackerRef.isValid()) {
                Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
                if (attackerPlayer != null) {
                    attackerInfo = attackerPlayer.getPlayerRef().getUuid().toString().substring(0, 8);
                }
            }
        }

        LOGGER.atInfo().log("[Hex:Consume] target=%s attacker=%s bonus=+%.1f%% damage=%.1f->%.1f",
                targetRef, attackerInfo, bonus * 100, original, hexed);
    }
}
