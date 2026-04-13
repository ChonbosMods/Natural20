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
 * Filter Group companion for Gallant: reduces outgoing damage from
 * entities that have the Gallant debuff applied by an armor wearer.
 */
public class Nat20GallantReduceSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20GallantSystem gallantSystem;

    public Nat20GallantReduceSystem(Nat20GallantSystem gallantSystem) {
        this.gallantSystem = gallantSystem;
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

        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        double reduction = gallantSystem.getGallantReduction(attackerRef);
        if (reduction <= 0) return;

        float original = damage.getAmount();
        float reduced = (float) (original * (1.0 - reduction));
        if (reduced < 0f) reduced = 0f;
        damage.setAmount(reduced);

        LOGGER.atInfo().log("[Gallant:Reduce] attacker=%s reduction=%.1f%% damage=%.1f->%.1f",
                attackerRef, reduction * 100, original, reduced);
    }
}
