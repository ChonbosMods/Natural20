package com.chonbosmods.loot.mob.abilities;

import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleporting mob ability: on hit, blinks to a random position within 8 blocks.
 *
 * <p>Has a 5-second cooldown between teleports to prevent excessive blinking. The actual
 * position change is a TODO pending SDK entity-position API discovery.
 */
public class TeleportingAbility implements MobAbilityHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /** Maximum teleport distance in blocks from current position. */
    private static final double TELEPORT_RANGE_BLOCKS = 8.0;

    /** Cooldown between teleports in milliseconds. */
    private static final long COOLDOWN_MS = 5_000L;

    /** Per-mob cooldown tracking: mob ref to last teleport timestamp in millis. */
    private final ConcurrentHashMap<Ref<EntityStore>, Long> cooldowns = new ConcurrentHashMap<>();

    @Override
    public void onSpawn(Ref<EntityStore> mobRef, Store<EntityStore> store, Nat20MobAffixDef def) {
        LOGGER.atInfo().log("Teleporting ability initialized for mob %s (affix: %s)", mobRef, def.id());
    }

    @Override
    public void onHurt(Ref<EntityStore> mobRef, Store<EntityStore> store, Damage event) {
        long now = System.currentTimeMillis();
        Long lastTeleport = cooldowns.get(mobRef);

        if (lastTeleport != null && (now - lastTeleport) < COOLDOWN_MS) {
            return; // On cooldown
        }

        cooldowns.put(mobRef, now);

        // TODO: Read mob's current position from its transform/position component.
        // Generate a random offset within TELEPORT_RANGE_BLOCKS in X/Z (and validate Y).
        // Set the mob's position to the new location. Requires entity position read/write API
        // and possibly a ground-check to avoid teleporting into blocks or mid-air.
        LOGGER.atInfo().log("Teleporting proc: mob %s blinks up to %.1f blocks away (hit for %.2f damage)",
                mobRef, TELEPORT_RANGE_BLOCKS, event.getAmount());
    }

    /**
     * Clean up per-mob state when a mob is removed.
     * Should be called from {@link com.chonbosmods.loot.mob.Nat20MobAffixManager#clearMob}.
     */
    public void clearMob(Ref<EntityStore> mobRef) {
        cooldowns.remove(mobRef);
    }
}
