package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS ticking system that bridges the DEX ability score modifier to the
 * player's movement speed via {@link MovementManager}.
 *
 * <p>Speed multiplier: {@code 1.0 + (dexMod * 0.04)} (4% per positive DEX
 * modifier point). Negative DEX modifiers are ignored: movement speed is
 * never reduced below base.
 *
 * <p>Because movement speed is not persisted across chunk loads, this system
 * includes drift reconciliation: each tick it checks the current baseSpeed
 * against the expected value and re-applies if it has drifted beyond a
 * small tolerance.
 */
public class Nat20MovementSpeedSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /** 4% speed increase per DEX modifier point. */
    private static final float SPEED_PER_MOD = 0.04f;

    /** Tolerance for drift detection: re-apply if current differs by more than this. */
    private static final float DRIFT_TOLERANCE = 0.02f;

    /** Minimum maxSpeedMultiplier to allow boosted speed to take effect. */
    private static final float MIN_MAX_SPEED_MULTIPLIER = 3.0f;

    private final Query<EntityStore> query;

    /** Tracks the expected speed multiplier per player for drift reconciliation. */
    private final ConcurrentHashMap<UUID, Float> expectedMultipliers = new ConcurrentHashMap<>();

    public Nat20MovementSpeedSystem() {
        this.query = Query.and(new Query[]{
                Query.any(), UUIDComponent.getComponentType(), Player.getComponentType()
        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID uuid = uuidComp.getUuid();

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        if (playerData == null) return;

        MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
        if (mm == null) return;

        PlayerStats stats = PlayerStats.from(playerData);
        int dexMod = stats.getPowerModifier(Stat.DEX);

        // Only positive DEX mod increases speed; negative does not reduce below base
        float multiplier = 1.0f + Math.max(0, dexMod) * SPEED_PER_MOD;

        MovementSettings current = mm.getSettings();
        MovementSettings defaults = mm.getDefaultSettings();
        float defaultBase = defaults.baseSpeed;
        float targetSpeed = defaultBase * multiplier;

        // Check if we need to apply: multiplier changed or drift detected
        Float previousMultiplier = expectedMultipliers.get(uuid);
        boolean multiplierChanged = previousMultiplier == null || Float.compare(previousMultiplier, multiplier) != 0;
        boolean drifted = !multiplierChanged && Math.abs(current.baseSpeed - targetSpeed) > DRIFT_TOLERANCE;

        if (!multiplierChanged && !drifted) return;

        // Apply speed
        current.baseSpeed = targetSpeed;

        // Raise maxSpeedMultiplier if needed so the engine doesn't clamp our boost
        if (current.maxSpeedMultiplier < MIN_MAX_SPEED_MULTIPLIER) {
            current.maxSpeedMultiplier = MIN_MAX_SPEED_MULTIPLIER;
        }

        // Sync to client
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            PlayerRef playerRef = player.getPlayerRef();
            mm.update(playerRef.getPacketHandler());
        }

        expectedMultipliers.put(uuid, multiplier);

        if (CombatDebugSystem.isEnabled(uuid)) {
            if (drifted) {
                LOGGER.atInfo().log("[MovementSpeed] player=%s RECONCILE: drift detected, re-applied multiplier=%.2f baseSpeed=%.3f",
                        uuid.toString().substring(0, 8), multiplier, targetSpeed);
            } else {
                LOGGER.atInfo().log("[MovementSpeed] player=%s DEX=%+d multiplier=%.2f baseSpeed=%.3f",
                        uuid.toString().substring(0, 8), dexMod, multiplier, targetSpeed);
            }
        }
    }

    /** Clean up tracked state on disconnect. */
    public void removePlayer(UUID uuid) {
        expectedMultipliers.remove(uuid);
    }
}
