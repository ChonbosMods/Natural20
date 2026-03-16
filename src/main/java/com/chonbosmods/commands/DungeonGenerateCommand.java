package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.dungeon.DungeonGenerator;
import com.chonbosmods.dungeon.DungeonGeneratorConfig;
import com.chonbosmods.dungeon.DungeonPieceRegistry;
import com.chonbosmods.dungeon.DungeonSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
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
import java.util.Random;

public class DungeonGenerateCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> minArg =
        withRequiredArg("min", "Minimum number of pieces", ArgTypes.STRING);
    private final RequiredArg<String> maxArg =
        withRequiredArg("max", "Maximum number of pieces", ArgTypes.STRING);
    private final OptionalArg<String> seedArg =
        withOptionalArg("seed", "Random seed (optional)", ArgTypes.STRING);

    public DungeonGenerateCommand() {
        super("dungeon", "Generate a dungeon at your position");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        // Parse min and max from string args
        int min;
        int max;
        try {
            min = Integer.parseInt(minArg.get(context));
        } catch (NumberFormatException e) {
            context.sendMessage(Message.raw("Invalid min value: must be an integer."));
            return;
        }
        try {
            max = Integer.parseInt(maxArg.get(context));
        } catch (NumberFormatException e) {
            context.sendMessage(Message.raw("Invalid max value: must be an integer."));
            return;
        }

        // Validate ranges
        if (min < 1) {
            context.sendMessage(Message.raw("min must be >= 1."));
            return;
        }
        if (max < min) {
            context.sendMessage(Message.raw("max must be >= min."));
            return;
        }

        // Check registry has definitions
        DungeonSystem dungeonSystem = Natural20.getInstance().getDungeonSystem();
        DungeonPieceRegistry pieceRegistry = dungeonSystem.getPieceRegistry();
        if (pieceRegistry.getDefCount() == 0) {
            context.sendMessage(Message.raw("No dungeon piece definitions loaded. Save pieces with /gridprefab save first."));
            return;
        }

        // Get player position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3i anchor = new Vector3i((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

        // Create Random: seeded if seed arg provided, otherwise default
        final Random random;
        if (context.provided(seedArg)) {
            String seedStr = seedArg.get(context);
            long seedValue;
            try {
                seedValue = Long.parseLong(seedStr);
            } catch (NumberFormatException e) {
                // Use string hash as seed if not a valid long
                seedValue = seedStr.hashCode();
            }
            random = new Random(seedValue);
        } else {
            random = new Random();
        }

        // Auto-detect guarantee tags: if any variant has "boss_room" tag, guarantee it
        List<String> guaranteeTags = new ArrayList<>();
        boolean hasBossRoom = pieceRegistry.getAllVariants().stream()
            .anyMatch(v -> v.getTags().contains("boss_room"));
        if (hasBossRoom) {
            guaranteeTags.add("boss_room");
        }

        // Create config
        DungeonGeneratorConfig config = new DungeonGeneratorConfig(min, max, anchor, guaranteeTags);

        context.sendMessage(Message.raw("Generating dungeon: min=" + min + " max=" + max
            + " at " + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ()
            + " (" + pieceRegistry.getDefCount() + " defs, " + pieceRegistry.getVariantCount() + " variants)"
            + (guaranteeTags.isEmpty() ? "" : " guarantees=" + guaranteeTags)));

        // Generate inside world.execute()
        world.execute(() -> {
            DungeonGenerator generator = new DungeonGenerator(dungeonSystem, config, random);
            generator.generate(world);

            int pieceCount = generator.getPlacedPieces().size();
            int connectionCount = generator.getConnections().size();
            context.sendMessage(Message.raw("Dungeon complete: " + pieceCount + " pieces, "
                + connectionCount + " connections."));
        });
    }
}
