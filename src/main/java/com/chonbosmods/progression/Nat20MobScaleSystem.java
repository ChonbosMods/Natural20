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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/**
 * Authority for monster level/tier scaling. Hooks every entity add; tags any
 * NPCEntity with a {@link Nat20MobLevel} component (default tier REGULAR)
 * and applies HP/damage modifiers. Spawners can call {@link #setTier} to
 * upgrade a mob's tier after spawn (rescale-on-upgrade).
 */
public class Nat20MobScaleSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobScale");
    private static final Query<EntityStore> QUERY = Query.any();

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
        // applyStats intentionally deferred to Task 3.2.
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
        if (level == null) return;
        level.setTier(newTier);
        // applyStats intentionally deferred to Task 3.2.
        LOGGER.atInfo().log("Retier ref=%s areaLevel=%d tier=%s",
                ref, level.getAreaLevel(), newTier);
    }
}
