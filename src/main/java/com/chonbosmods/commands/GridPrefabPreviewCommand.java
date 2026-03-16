package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.BlockData;
import com.chonbosmods.dungeon.DungeonSystem;
import com.chonbosmods.dungeon.Face;
import com.chonbosmods.dungeon.GridPrefabUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Pastes a registered dungeon prefab based on the player's crosshair target and facing direction.
 * Usage: /gridprefab preview <name>
 *
 * Looks up the name in dungeon_pieces first, then dungeon_connectors.
 * Loads the .blocks.json file and pastes block by block, transforming canonical
 * local coordinates to world space based on the player's cardinal facing direction.
 */
public class GridPrefabPreviewCommand extends AbstractPlayerCommand {

    private static final double RAYCAST_MAX_DISTANCE = 50.0;

    private final RequiredArg<String> nameArg =
        withRequiredArg("name", "Piece or connector name", ArgTypes.STRING);

    public GridPrefabPreviewCommand() {
        super("preview", "Paste a registered prefab at your crosshair target");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String name = nameArg.get(context);

        DungeonSystem dungeonSystem = Natural20.getInstance().getDungeonSystem();

        // Try dungeon_pieces first, then dungeon_connectors
        BlockData data = dungeonSystem.loadBlockData(name, "dungeon_pieces");
        String sourceType = "piece";

        if (data == null) {
            data = dungeonSystem.loadBlockData(name, "dungeon_connectors");
            sourceType = "connector";
        }

        if (data == null) {
            context.sendMessage(Message.raw("No .blocks.json found for: " + name));
            return;
        }

        // Get player position and rotation
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        float yaw = rot.getY();
        float pitch = rot.getX();

        Face facing = GridPrefabUtil.getCardinalFacing(yaw);
        Vector3d direction = GridPrefabUtil.getDirection(yaw, pitch);

        // Eye position is approximately 1.6 blocks above foot position
        Vector3d eyePos = new Vector3d(pos.getX(), pos.getY() + 1.6, pos.getZ());

        int blockW = data.width();
        int blockD = data.depth();

        // Capture for lambda
        final BlockData finalData = data;
        final String finalSourceType = sourceType;

        world.execute(() -> {
            // Raycast to find target block
            Vector3i target = GridPrefabUtil.getTargetBlock(world, eyePos, direction, RAYCAST_MAX_DISTANCE);
            if (target == null) {
                context.sendMessage(Message.raw("No block in view (look at a block to set the origin)."));
                return;
            }

            Vector3i origin = GridPrefabUtil.computeRegionOrigin(target, facing, blockW, blockD);

            context.sendMessage(Message.raw("Pasting " + finalSourceType + " '" + name + "' (" +
                finalData.blocks().size() + " blocks) at " +
                origin.getX() + "," + origin.getY() + "," + origin.getZ() +
                " facing " + facing + "..."));

            int placed = 0;
            for (BlockData.BlockEntry entry : finalData.blocks()) {
                // Transform canonical local coords to world-space offset
                int[] worldOffset = GridPrefabUtil.localToWorld(entry.x(), entry.z(), facing, blockW, blockD);
                int bx = origin.getX() + worldOffset[0];
                int by = origin.getY() + entry.y();
                int bz = origin.getZ() + worldOffset[1];

                // Place block
                world.setBlock(bx, by, bz, entry.id());

                // Apply rotation via chunk's BlockAccessor if needed
                if (entry.rot() != 0) {
                    long chunkKey = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(bx, bz);
                    var chunk = world.getChunkIfLoaded(chunkKey);
                    if (chunk != null) {
                        int blockId = world.getBlock(bx, by, bz);
                        BlockType blockType = world.getBlockType(bx, by, bz);
                        if (blockType != null) {
                            chunk.setBlock(bx, by, bz, blockId, blockType, entry.rot(), 0, 0);
                        }
                    }
                }
                placed++;
            }
            context.sendMessage(Message.raw("Pasted " + finalSourceType + " '" + name +
                "': " + placed + " blocks placed."));
        });
    }
}
