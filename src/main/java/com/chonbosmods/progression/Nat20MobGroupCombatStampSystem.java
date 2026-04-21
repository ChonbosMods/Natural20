package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.mob.Nat20MobGroupMemberComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Stamps {@code lastCombatMillis} on {@link Nat20MobGroupMemberComponent} whenever a
 * group-member mob is involved in a damage event, either as victim or as attacker.
 *
 * <p>Read by {@code Nat20MobGroupLeashSystem} as the "recently in combat" signal. This
 * replaces the old behavior-tree target-slot check, which snapshotted current-moment
 * target refs and missed the window where a player runs out of view and the mob's
 * target ref invalidates before combat has truly ended. The timestamp approach is
 * data-driven and rides chunk unload/reload because the component is persisted via
 * {@link Nat20MobGroupMemberComponent#CODEC}.
 */
public class Nat20MobGroupCombatStampSystem extends DamageEventSystem {

    private static final Query<EntityStore> QUERY = Query.any();

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> cb,
                       Damage damage) {
        if (damage.isCancelled()) return;

        long now = System.currentTimeMillis();

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityIndex);
        stamp(store, victimRef, now);

        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef != null && attackerRef.isValid()) {
                stamp(store, attackerRef, now);
            }
        }
    }

    private static void stamp(Store<EntityStore> store, Ref<EntityStore> ref, long nowMs) {
        if (ref == null || !ref.isValid()) return;
        Nat20MobGroupMemberComponent member =
                store.getComponent(ref, Natural20.getMobGroupMemberType());
        if (member == null) return;
        member.setLastCombatMillis(nowMs);
    }
}
