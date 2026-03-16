package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.ConnectorDef;
import com.chonbosmods.dungeon.ConnectorRegistry;
import com.chonbosmods.dungeon.DungeonPieceDef;
import com.chonbosmods.dungeon.DungeonPieceRegistry;
import com.chonbosmods.dungeon.SocketEntry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Lists all registered dungeon pieces and connectors.
 * Usage: /gridprefab list
 */
public class GridPrefabListCommand extends AbstractPlayerCommand {

    public GridPrefabListCommand() {
        super("list", "List all registered dungeon pieces and connectors");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        DungeonPieceRegistry pieceRegistry = Natural20.getInstance().getDungeonSystem().getPieceRegistry();
        ConnectorRegistry connectorRegistry = Natural20.getInstance().getDungeonSystem().getConnectorRegistry();

        // Pieces header
        context.sendMessage(Message.raw("--- Dungeon Pieces (" +
            pieceRegistry.getDefCount() + " defs, " +
            pieceRegistry.getVariantCount() + " variants) ---"));

        for (DungeonPieceDef def : pieceRegistry.getAllDefs()) {
            int blockW = def.gridWidth() * 5;
            int blockH = def.gridHeight() * 5;
            int blockD = def.gridDepth() * 5;

            long openCount = def.sockets().stream().filter(SocketEntry::isOpen).count();
            long sealedCount = def.sockets().size() - openCount;

            StringBuilder line = new StringBuilder();
            line.append("  ").append(def.name());
            line.append(" [").append(blockW).append("x").append(blockH).append("x").append(blockD).append("]");
            line.append(" sockets:").append(openCount).append("o/").append(sealedCount).append("s");
            line.append(" tags:").append(def.tags());
            line.append(" w:").append(formatWeight(def.weight()));
            if (def.rotatable()) {
                line.append(" [rotatable]");
            }

            context.sendMessage(Message.raw(line.toString()));
        }

        // Connectors header
        context.sendMessage(Message.raw("--- Connectors (" +
            connectorRegistry.getDefCount() + ") ---"));

        for (ConnectorDef def : connectorRegistry.getAllDefs()) {
            StringBuilder line = new StringBuilder();
            line.append("  ").append(def.name());
            line.append(" tags:").append(def.tags());
            line.append(" w:").append(formatWeight(def.weight()));

            context.sendMessage(Message.raw(line.toString()));
        }
    }

    private String formatWeight(double weight) {
        if (weight == (long) weight) {
            return String.valueOf((long) weight);
        }
        return String.valueOf(weight);
    }
}
