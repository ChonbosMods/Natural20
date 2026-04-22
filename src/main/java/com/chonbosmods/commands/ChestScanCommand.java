package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.loot.chest.Nat20ChestChunkScanner;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Debug: runs {@link Nat20ChestChunkScanner#onChunkLoad} on the chunk the player is
 * standing in, right now, with the chunk fully loaded. Tells us whether the scanner's
 * block-iteration logic is correct vs whether ChunkPreLoadProcessEvent is firing too
 * early to see native prefab chests.
 */
public class ChestScanCommand extends AbstractPlayerCommand {

    public ChestScanCommand() {
        super("chestscan", "Scan the chunk you're standing in for chests (debug)");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Nat20ChestChunkScanner scanner = Natural20.getInstance().getChestChunkScanner();
        if (scanner == null) {
            context.sendMessage(Message.raw("Chest scanner not initialized"));
            return;
        }

        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t == null) {
            context.sendMessage(Message.raw("No transform"));
            return;
        }
        Vector3d pos = t.getPosition();
        int chunkBlockX = ((int) Math.floor(pos.getX()) >> 5) << 5;
        int chunkBlockZ = ((int) Math.floor(pos.getZ()) >> 5) << 5;
        long chunkKey = ChunkUtil.indexChunk(
                ChunkUtil.chunkCoordinate(chunkBlockX),
                ChunkUtil.chunkCoordinate(chunkBlockZ));
        WorldChunk chunk = world.getChunkIfLoaded(chunkKey);
        if (chunk == null) {
            context.sendMessage(Message.raw("Chunk not loaded at " + chunkBlockX + "," + chunkBlockZ));
            return;
        }

        context.sendMessage(Message.raw(String.format(
                "Scanning chunk (%d,%d) at player pos %.0f,%.0f,%.0f - see server log",
                chunkBlockX >> 5, chunkBlockZ >> 5, pos.getX(), pos.getY(), pos.getZ())));
        scanner.onChunkLoad(world, chunk, chunkBlockX, chunkBlockZ);
    }
}
