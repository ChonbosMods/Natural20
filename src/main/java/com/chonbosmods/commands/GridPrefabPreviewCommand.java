package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.BlockData;
import com.chonbosmods.dungeon.ConnectorDef;
import com.chonbosmods.dungeon.DungeonPieceDef;
import com.chonbosmods.dungeon.DungeonSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
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
 * Pastes a registered dungeon prefab at the player's position for preview.
 * Usage: /gridprefab preview <name>
 *
 * Looks up the name in DungeonPieceRegistry first, then ConnectorRegistry.
 * Loads the .blocks.json file and replays blocks via world.setBlock().
 */
public class GridPrefabPreviewCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> nameArg =
        withRequiredArg("name", "Piece or connector name", ArgTypes.STRING);

    public GridPrefabPreviewCommand() {
        super("preview", "Paste a registered prefab at your position");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String name = nameArg.get(context);

        DungeonSystem dungeonSystem = Natural20.getInstance().getDungeonSystem();

        // Look up in piece registry first, then connector registry
        String blockDataType = null;
        String sourceType = null;

        DungeonPieceDef pieceDef = dungeonSystem.getPieceRegistry().getDef(name);
        if (pieceDef != null) {
            blockDataType = "dungeon_pieces";
            sourceType = "piece";
        } else {
            ConnectorDef connectorDef = dungeonSystem.getConnectorRegistry().getDef(name);
            if (connectorDef != null) {
                blockDataType = "dungeon_connectors";
                sourceType = "connector";
            }
        }

        if (sourceType == null) {
            context.sendMessage(Message.raw("No piece or connector found with name: " + name));
            return;
        }

        // Load block data
        BlockData blockData = dungeonSystem.loadBlockData(name, blockDataType);
        if (blockData == null) {
            context.sendMessage(Message.raw("Failed to load block data for: " + name));
            return;
        }

        // Get player position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        int originX = (int) pos.getX();
        int originY = (int) pos.getY();
        int originZ = (int) pos.getZ();

        context.sendMessage(Message.raw("Pasting " + sourceType + " '" + name + "' at " +
            originX + ", " + originY + ", " + originZ + "..."));

        // Capture for lambda
        final String finalSourceType = sourceType;

        world.execute(() -> {
            for (BlockData.BlockEntry entry : blockData.blocks()) {
                world.setBlock(originX + entry.x(), originY + entry.y(), originZ + entry.z(), entry.id());
            }

            context.sendMessage(Message.raw("Pasted " + finalSourceType + " '" + name +
                "' successfully (" + blockData.blocks().size() + " blocks)."));
        });
    }
}
