package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.cave.CaveVoidScanner;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
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
import java.util.Comparator;
import java.util.List;

public class CaveVoidsCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> operationArg =
            withRequiredArg("operation", "scan [radius], list, nearest, place, clear", ArgTypes.STRING);
    private final OptionalArg<String> extraArg =
            withOptionalArg("args", "Optional arguments (e.g. radius for scan)", ArgTypes.STRING);

    public CaveVoidsCommand() {
        super("cavevoids", "Cave void debug commands");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String operation = operationArg.get(context);

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }
        Vector3d playerPos = transform.getPosition();

        switch (operation.toLowerCase()) {
            case "scan" -> executeScan(context, world, playerPos);
            case "list" -> executeList(context, playerPos);
            case "nearest" -> executeNearest(context, playerPos);
            case "place" -> context.sendMessage(Message.raw("Place operation: coming in Task 8"));
            case "clear" -> executeClear(context);
            default -> context.sendMessage(Message.raw(
                    "Unknown operation: " + operation + ". Use: scan, list, nearest, place, clear"));
        }
    }

    private void executeScan(CommandContext context, World world, Vector3d playerPos) {
        String extra = extraArg.get(context);
        int radius = 200;
        if (extra != null && !extra.isEmpty()) {
            try {
                radius = Integer.parseInt(extra);
            } catch (NumberFormatException e) {
                context.sendMessage(Message.raw("Invalid radius: " + extra + ". Using default 200."));
            }
        }

        CaveVoidScanner scanner = Natural20.getInstance().getCaveVoidScanner();
        CaveVoidRegistry registry = Natural20.getInstance().getCaveVoidRegistry();

        int playerChunkX = ChunkUtil.chunkCoordinate((int) playerPos.getX());
        int playerChunkZ = ChunkUtil.chunkCoordinate((int) playerPos.getZ());
        int chunkRadius = radius / 32;
        int scanned = 0;

        context.sendMessage(Message.raw("Scanning chunks within " + radius + " blocks..."));

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                scanner.scanChunk(world, cx * 32, cz * 32);
                scanned++;
            }
        }

        context.sendMessage(Message.raw("Scanned " + scanned + " chunks. Registry total: " + registry.getCount() + " void(s)."));
    }

    private void executeList(CommandContext context, Vector3d playerPos) {
        CaveVoidRegistry registry = Natural20.getInstance().getCaveVoidRegistry();
        List<CaveVoidRecord> all = registry.getAll();

        if (all.isEmpty()) {
            context.sendMessage(Message.raw("No cave voids registered. Run 'scan' first."));
            return;
        }

        int px = (int) playerPos.getX();
        int pz = (int) playerPos.getZ();

        context.sendMessage(Message.raw("Cave voids: " + all.size() + " total"));

        all.stream()
            .sorted(Comparator.comparingInt(v -> v.distanceTo(px, pz)))
            .limit(20)
            .forEach(v -> {
                int dist = v.distanceTo(px, pz);
                String claimed = v.isClaimed() ? " [CLAIMED by " + v.getClaimedBySettlement() + "]" : "";
                context.sendMessage(Message.raw("  (" + v.getCenterX() + ", " + v.getCenterY() + ", " + v.getCenterZ() +
                        ") vol=" + v.getVolume() + " dist=" + dist + "m" + claimed));
            });

        if (all.size() > 20) {
            context.sendMessage(Message.raw("  ... and " + (all.size() - 20) + " more"));
        }
    }

    private void executeNearest(CommandContext context, Vector3d playerPos) {
        CaveVoidRegistry registry = Natural20.getInstance().getCaveVoidRegistry();
        int px = (int) playerPos.getX();
        int pz = (int) playerPos.getZ();

        CaveVoidRecord nearest = registry.findAnyVoid(px, pz);
        if (nearest == null) {
            context.sendMessage(Message.raw("No unclaimed cave voids found. Run 'scan' first."));
            return;
        }

        int dist = nearest.distanceTo(px, pz);
        context.sendMessage(Message.raw("Nearest unclaimed void: (" + nearest.getCenterX() + ", " +
                nearest.getCenterY() + ", " + nearest.getCenterZ() + ") vol=" + nearest.getVolume() +
                " dist=" + dist + "m"));
        context.sendMessage(Message.raw("Use: /tp " + nearest.getCenterX() + " " +
                nearest.getCenterY() + " " + nearest.getCenterZ()));
    }

    private void executeClear(CommandContext context) {
        CaveVoidRegistry registry = Natural20.getInstance().getCaveVoidRegistry();
        int count = registry.getCount();
        registry.clear();
        context.sendMessage(Message.raw("Cleared " + count + " cave void(s) from registry."));
    }
}
