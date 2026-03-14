package com.chonbosmods.loot.effects;

import com.chonbosmods.loot.def.Nat20AffixDef;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;

/**
 * Telepathic auto-loot: teleports block drops directly to the player's inventory.
 *
 * <p>The {@code effectiveValue} is always 1.0 (boolean ability: either active or not).
 * When active, block drops skip the ground and go straight into the player's inventory.
 */
public class TelepathicHandler implements EffectHandler {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    @Override
    public void onBlockBreak(Nat20AffixDef def, double effectiveValue, BreakBlockEvent event) {
        if (effectiveValue <= 0) return;

        // TODO: Intercept block drops and add them to the player's inventory.
        // Potential approaches:
        //   1. Cancel the default drop, compute the block's drop table, and add items via
        //      player.getInventory().addItem(). Requires SDK drop table API.
        //   2. Set a flag on the event (if supported) to redirect drops to the player.
        //   3. Hook into a post-break event to collect dropped item entities near the block
        //      and move them to inventory.
        //
        // Pseudocode:
        //   Player player = event.getPlayer();
        //   List<ItemStack> drops = event.getDrops(); // or compute from block type
        //   for (ItemStack drop : drops) {
        //       player.getInventory().addItem(drop);
        //   }
        //   event.setDropItems(false); // prevent ground drops

        LOGGER.atInfo().log("Telepathic proc: block drops should teleport to player inventory");
    }
}
