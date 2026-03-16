package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.ConnectorDef;
import com.chonbosmods.dungeon.DungeonPieceDef;
import com.chonbosmods.dungeon.DungeonSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Pastes a registered dungeon prefab at the player's position for preview.
 * Usage: /gridprefab preview <name>
 *
 * Looks up the name in DungeonPieceRegistry first, then ConnectorRegistry.
 * Loads the .prefab.json file and pastes via the vanilla PrefabUtil pipeline.
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
        String prefabKey = null;
        String sourceType = null;

        DungeonPieceDef pieceDef = dungeonSystem.getPieceRegistry().getDef(name);
        if (pieceDef != null) {
            prefabKey = pieceDef.prefabKey();
            sourceType = "piece";
        } else {
            ConnectorDef connectorDef = dungeonSystem.getConnectorRegistry().getDef(name);
            if (connectorDef != null) {
                prefabKey = connectorDef.prefabKey();
                sourceType = "connector";
            }
        }

        if (sourceType == null) {
            context.sendMessage(Message.raw("No piece or connector found with name: " + name));
            return;
        }

        // Load prefab buffer
        IPrefabBuffer buffer = dungeonSystem.getPrefabBuffer(prefabKey);
        if (buffer == null) {
            context.sendMessage(Message.raw("Failed to load prefab: " + prefabKey));
            return;
        }

        // Get player position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3i position = new Vector3i((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

        context.sendMessage(Message.raw("Pasting " + sourceType + " '" + name + "' at " +
            position.getX() + ", " + position.getY() + ", " + position.getZ() + "..."));

        // Capture for lambda
        final String finalSourceType = sourceType;

        world.execute(() -> {
            PrefabUtil.paste(buffer, world, position, Rotation.None, true,
                new Random(), 0, store);
            context.sendMessage(Message.raw("Pasted " + finalSourceType + " '" + name + "' successfully."));
        });
    }
}
