package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Records every non-cancelled damage event from a player source against a
 * Nat20-scaled mob into {@link Nat20DamageContributorTracker}. Zero-damage
 * hits (fully mitigated by armor, etc.) are still recorded: intent counts.
 *
 * <p>Settlement NPCs are skipped: they're not XP or Nat20-loot sources, and
 * we don't want to conflate "attacking a shopkeeper" with contribution.
 *
 * <p>Registered before {@link Nat20XpOnKillSystem} and
 * {@code Nat20MobLootDropSystem} so the lethal-damage lookup sees a fresh
 * write. Those systems also call {@code tracker.recordFromDamage} defensively
 * in case ECS system order is not strictly guaranteed.
 */
public class Nat20ContributorTrackingSystem extends DamageEventSystem {

    private static final Query<EntityStore> QUERY = Query.any();

    private final Nat20DamageContributorTracker tracker;

    public Nat20ContributorTrackingSystem(Nat20DamageContributorTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> cb,
                       Damage damage) {
        if (damage.isCancelled()) return;

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityIndex);

        // Only track damage to Nat20-scaled mobs.
        if (store.getComponent(victimRef, Natural20.getMobLevelType()) == null) return;

        // Skip settlement NPCs: attacking a shopkeeper does not count as a contribution.
        Nat20NpcData npcData = store.getComponent(victimRef, Natural20.getNpcDataType());
        if (npcData != null && npcData.getSettlementCellKey() != null) return;

        tracker.recordFromDamage(victimRef, damage, store, System.currentTimeMillis());
    }
}
