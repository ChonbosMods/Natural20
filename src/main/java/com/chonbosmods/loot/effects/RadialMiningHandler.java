package com.chonbosmods.loot.effects;

import com.chonbosmods.loot.def.Nat20AffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;

/**
 * Radial mining: breaks a 3x3 area centered on the target block.
 *
 * <p>The {@code effectiveValue} represents the radius (3.0 = 3x3 area).
 * A re-entrancy guard prevents infinite recursion when adjacent block breaks
 * trigger additional BreakBlockEvents.
 */
public class RadialMiningHandler implements EffectHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /**
     * Re-entrancy guard: tracks whether this handler is currently processing a radial break
     * on the current thread. Prevents infinite recursion when programmatically broken blocks
     * fire additional BreakBlockEvents.
     */
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    public void onBlockBreak(Nat20AffixDef def, double effectiveValue, BreakBlockEvent event) {
        if (effectiveValue <= 0) return;

        // Re-entrancy guard: skip if we're already processing a radial break on this thread
        if (PROCESSING.get()) return;

        int radius = Math.max(1, (int) effectiveValue / 2);

        // TODO: Get target block position from event.getTargetBlock() (returns Vector3i or similar).
        // Iterate surrounding blocks in a (2*radius+1)^3 cube (or same-Y plane for 2D radial)
        // and break each one programmatically via World.setBlock() or similar SDK API.
        //
        // Pseudocode:
        //   Vector3i center = event.getTargetBlock();
        //   World world = event.getWorld();
        //   PROCESSING.set(true);
        //   try {
        //       for (int dx = -radius; dx <= radius; dx++) {
        //           for (int dy = -radius; dy <= radius; dy++) {
        //               for (int dz = -radius; dz <= radius; dz++) {
        //                   if (dx == 0 && dy == 0 && dz == 0) continue; // skip center
        //                   Vector3i pos = center.add(dx, dy, dz);
        //                   world.breakBlock(pos); // or equivalent SDK call
        //               }
        //           }
        //       }
        //   } finally {
        //       PROCESSING.set(false);
        //   }

        LOGGER.atInfo().log("Radial mining proc: breaking %dx%dx%d area (radius=%d) around target block",
                2 * radius + 1, 2 * radius + 1, 2 * radius + 1, radius);
    }
}
