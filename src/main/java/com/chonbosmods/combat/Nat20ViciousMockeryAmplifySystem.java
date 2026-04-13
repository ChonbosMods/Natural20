package com.chonbosmods.combat;

import com.google.common.flogger.FluentLogger;
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
 * Filter Group companion for Vicious Mockery: amplifies all incoming damage
 * to targets that have the Vicious Mockery debuff active.
 */
public class Nat20ViciousMockeryAmplifySystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20ViciousMockerySystem mockerySystem;

    public Nat20ViciousMockeryAmplifySystem(Nat20ViciousMockerySystem mockerySystem) {
        this.mockerySystem = mockerySystem;
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
        double amplify = mockerySystem.getAmplifyMultiplier(targetRef);
        if (amplify <= 0) return;

        float original = damage.getAmount();
        float amplified = (float) (original * (1.0 + amplify));
        damage.setAmount(amplified);

        LOGGER.atInfo().log("[ViciousMockery:Amplify] target=%s amplify=+%.1f%% damage=%.1f->%.1f",
                targetRef, amplify * 100, original, amplified);
    }
}
