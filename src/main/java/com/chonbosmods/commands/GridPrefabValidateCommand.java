package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.DungeonPieceDef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Re-validates grid rules for a dungeon piece at the player's position.
 * Usage: /gridprefab validate <name>
 *
 * Checks wall integrity, floor, and ceiling for gaps.
 */
public class GridPrefabValidateCommand extends AbstractPlayerCommand {

    private static final int CELL_SIZE = 5;
    private static final int MAX_ISSUES_SHOWN = 20;

    private final RequiredArg<String> nameArg =
        withRequiredArg("name", "Piece name to validate", ArgTypes.STRING);

    public GridPrefabValidateCommand() {
        super("validate", "Re-validate grid rules at your position");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String name = nameArg.get(context);

        DungeonPieceDef def = Natural20.getInstance().getDungeonSystem().getPieceRegistry().getDef(name);
        if (def == null) {
            context.sendMessage(Message.raw("No piece found with name: " + name));
            return;
        }

        // Get player position as origin
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        int originX = (int) pos.getX();
        int originY = (int) pos.getY();
        int originZ = (int) pos.getZ();

        int blockW = def.gridWidth() * CELL_SIZE;
        int blockH = def.gridHeight() * CELL_SIZE;
        int blockD = def.gridDepth() * CELL_SIZE;

        context.sendMessage(Message.raw("Validating '" + name + "' (" +
            blockW + "x" + blockH + "x" + blockD + ") at " +
            originX + ", " + originY + ", " + originZ + "..."));

        world.execute(() -> {
            List<String> issues = new ArrayList<>();

            // Check wall integrity: north wall (z=0)
            for (int x = 0; x < blockW; x++) {
                for (int y = 0; y <= 4; y++) {
                    BlockType bt = world.getBlockType(originX + x, originY + y, originZ);
                    if (bt == null) {
                        issues.add("Gap: north wall x=" + x + " y=" + y);
                    }
                }
            }

            // Check wall integrity: south wall (z=blockD-1)
            for (int x = 0; x < blockW; x++) {
                for (int y = 0; y <= 4; y++) {
                    BlockType bt = world.getBlockType(originX + x, originY + y, originZ + blockD - 1);
                    if (bt == null) {
                        issues.add("Gap: south wall x=" + x + " y=" + y);
                    }
                }
            }

            // Check wall integrity: west wall (x=0)
            for (int z = 0; z < blockD; z++) {
                for (int y = 0; y <= 4; y++) {
                    BlockType bt = world.getBlockType(originX, originY + y, originZ + z);
                    if (bt == null) {
                        issues.add("Gap: west wall z=" + z + " y=" + y);
                    }
                }
            }

            // Check wall integrity: east wall (x=blockW-1)
            for (int z = 0; z < blockD; z++) {
                for (int y = 0; y <= 4; y++) {
                    BlockType bt = world.getBlockType(originX + blockW - 1, originY + y, originZ + z);
                    if (bt == null) {
                        issues.add("Gap: east wall z=" + z + " y=" + y);
                    }
                }
            }

            // Check floor: all blocks at y=0 should be solid
            for (int x = 0; x < blockW; x++) {
                for (int z = 0; z < blockD; z++) {
                    BlockType bt = world.getBlockType(originX + x, originY, originZ + z);
                    if (bt == null) {
                        issues.add("Gap: floor x=" + x + " z=" + z);
                    }
                }
            }

            // Check ceiling: all blocks at y=5 should be solid (if blockH >= 6)
            if (blockH >= 6) {
                for (int x = 0; x < blockW; x++) {
                    for (int z = 0; z < blockD; z++) {
                        BlockType bt = world.getBlockType(originX + x, originY + 5, originZ + z);
                        if (bt == null) {
                            issues.add("Gap: ceiling x=" + x + " z=" + z);
                        }
                    }
                }
            }

            // Report results
            if (issues.isEmpty()) {
                context.sendMessage(Message.raw("Validation PASSED"));
            } else {
                context.sendMessage(Message.raw("Validation found " + issues.size() + " issues:"));
                int shown = Math.min(issues.size(), MAX_ISSUES_SHOWN);
                for (int i = 0; i < shown; i++) {
                    context.sendMessage(Message.raw("  " + issues.get(i)));
                }
                if (issues.size() > MAX_ISSUES_SHOWN) {
                    context.sendMessage(Message.raw("  ... and " +
                        (issues.size() - MAX_ISSUES_SHOWN) + " more"));
                }
            }
        });
    }
}
