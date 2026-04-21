package com.chonbosmods.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * {@code /nat20 wheream}: dump the player's position alongside the block
 * directly below feet so we can diagnose Y-alignment surprises during prefab
 * placement (e.g. "anchor lands 2 blocks below where expected").
 */
public class WhereAmICommand extends AbstractPlayerCommand {

    public WhereAmICommand() {
        super("wheream", "Report raw player position, int-cast anchor, and block-below-feet");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TransformComponent tf = store.getComponent(ref, TransformComponent.getComponentType());
        if (tf == null || tf.getPosition() == null) {
            ctx.sendMessage(Message.raw("No transform."));
            return;
        }
        Vector3d pos = tf.getPosition();

        int ix = (int) pos.getX();
        int iy = (int) pos.getY();
        int iz = (int) pos.getZ();

        int floorX = (int) Math.floor(pos.getX());
        int floorY = (int) Math.floor(pos.getY());
        int floorZ = (int) Math.floor(pos.getZ());

        // Walk down up to 8 blocks looking for the first solid block below the player.
        int firstSolidY = Integer.MIN_VALUE;
        String firstSolidId = "-";
        for (int dy = 0; dy < 8; dy++) {
            int y = floorY - dy;
            BlockType bt = world.getBlockType(floorX, y, floorZ);
            if (bt != null && bt.getMaterial() == BlockMaterial.Solid) {
                firstSolidY = y;
                firstSolidId = bt.getId() != null ? bt.getId() : "?";
                break;
            }
        }

        // Also check the block directly AT the player's feet Y (which might be air, solid, or empty).
        BlockType atFeet = world.getBlockType(floorX, floorY, floorZ);
        String atFeetId = (atFeet != null && atFeet.getId() != null) ? atFeet.getId() : "-";
        String atFeetMat = atFeet != null ? String.valueOf(atFeet.getMaterial()) : "-";

        BlockType below = world.getBlockType(floorX, floorY - 1, floorZ);
        String belowId = (below != null && below.getId() != null) ? below.getId() : "-";
        String belowMat = below != null ? String.valueOf(below.getMaterial()) : "-";

        ctx.sendMessage(Message.raw(String.format(
            "pos=(%.3f, %.3f, %.3f)", pos.getX(), pos.getY(), pos.getZ())));
        ctx.sendMessage(Message.raw(String.format(
            "(int) cast   = (%d, %d, %d)", ix, iy, iz)));
        ctx.sendMessage(Message.raw(String.format(
            "floor()      = (%d, %d, %d)", floorX, floorY, floorZ)));
        ctx.sendMessage(Message.raw(String.format(
            "  at feet Y=%d  -> %s  (%s)", floorY, atFeetId, atFeetMat)));
        ctx.sendMessage(Message.raw(String.format(
            "  below Y=%d   -> %s  (%s)", floorY - 1, belowId, belowMat)));
        if (firstSolidY != Integer.MIN_VALUE) {
            ctx.sendMessage(Message.raw(String.format(
                "  first solid below: Y=%d -> %s  (delta %d)",
                firstSolidY, firstSolidId, floorY - firstSolidY)));
        } else {
            ctx.sendMessage(Message.raw("  no solid block within 8 below"));
        }
    }
}
