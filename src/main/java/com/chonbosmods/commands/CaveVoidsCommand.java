package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.cave.CaveVoidScanner;
import com.chonbosmods.prefab.Nat20PrefabPath;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
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
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.util.PrefabUtil;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CaveVoidsCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> operationArg =
            withRequiredArg("operation", "scan [radius], list, nearest, place, stats, clear", ArgTypes.STRING);
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
            case "place" -> executePlace(context, world, playerPos, store);
            case "clear" -> executeClear(context);
            case "stats" -> executeStats(context, playerPos);
            case "probe" -> executeProbe(context, world, playerPos);
            case "pastehere" -> executePasteHere(context, world, playerPos, store);
            case "testpoi" -> executeTestPOI(context, world, playerPos, store, ref);
            default -> context.sendMessage(Message.raw(
                    "Unknown operation: " + operation + ". Use: scan, list, nearest, place, pastehere, probe, stats, testpoi, clear"));
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
                int spanX = v.getMaxX() - v.getMinX() + 1;
                int spanZ = v.getMaxZ() - v.getMinZ() + 1;
                String claimed = v.isClaimed() ? " [CLAIMED by " + v.getClaimedBySettlement() + "]" : "";
                context.sendMessage(Message.raw("  (" + v.getCenterX() + ", " + v.getCenterY() + ", " + v.getCenterZ() +
                        ") vol=" + v.getVolume() + " span=" + spanX + "x" + spanZ +
                        " dist=" + dist + "m" + claimed));
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

    private void executePlace(CommandContext context, World world, Vector3d playerPos,
                              Store<EntityStore> store) {
        CaveVoidRegistry registry = Natural20.getInstance().getCaveVoidRegistry();
        int px = (int) playerPos.getX();
        int pz = (int) playerPos.getZ();

        CaveVoidRecord nearest = registry.findAnyVoid(px, pz);
        if (nearest == null) {
            context.sendMessage(Message.raw("No unclaimed cave voids found. Run 'scan' first."));
            return;
        }

        int cx = nearest.getCenterX();
        int cy = nearest.getCenterY();
        int cz = nearest.getCenterZ();
        context.sendMessage(Message.raw("Placing structure at nearest void (" + cx + ", " + cy + ", " + cz + ")..."));

        Natural20.getInstance().getStructurePlacer()
                .placeAtVoid(world, nearest, store)
                .whenComplete((placed, error) -> {
                    if (error != null) {
                        context.sendMessage(Message.raw("Structure placement failed: " + error.getMessage()));
                        return;
                    }
                    if (placed == null) {
                        context.sendMessage(Message.raw("Structure placement failed: no entrance position returned."));
                        return;
                    }
                    Vector3i entrance = placed.anchorWorld();
                    context.sendMessage(Message.raw("Structure placed at (" +
                            entrance.getX() + ", " + entrance.getY() + ", " + entrance.getZ() +
                            ") connected to void at (" + cx + ", " + cy + ", " + cz + ")"));
                });
    }

    private void executeClear(CommandContext context) {
        CaveVoidRegistry registry = Natural20.getInstance().getCaveVoidRegistry();
        int count = registry.getCount();
        registry.clear();
        context.sendMessage(Message.raw("Cleared " + count + " cave void(s) from registry."));
    }

    private void executeStats(CommandContext context, Vector3d playerPos) {
        CaveVoidRegistry registry = Natural20.getInstance().getCaveVoidRegistry();
        List<CaveVoidRecord> all = registry.getAll();

        if (all.isEmpty()) {
            context.sendMessage(Message.raw("No cave voids registered. Run 'scan' first."));
            return;
        }

        int[] tiers = {25_000, 50_000, 100_000, 250_000};
        context.sendMessage(Message.raw("Cave void stats (" + all.size() + " total):"));
        for (int tier : tiers) {
            long count = all.stream().filter(v -> v.getVolume() >= tier).count();
            context.sendMessage(Message.raw("  " + tier + "+ blocks: " + count + " void(s)"));
        }
    }

    private void executeProbe(CommandContext context, World world, Vector3d playerPos) {
        int px = (int) Math.floor(playerPos.getX());
        int py = (int) Math.floor(playerPos.getY());
        int pz = (int) Math.floor(playerPos.getZ());

        context.sendMessage(Message.raw("Probing at (" + px + ", " + py + ", " + pz + ")"));

        // Scan air extent in each cardinal direction at foot level
        int airNegX = scanAir(world, px, py, pz, -1, 0);
        int airPosX = scanAir(world, px, py, pz, 1, 0);
        int airNegZ = scanAir(world, px, py, pz, 0, -1);
        int airPosZ = scanAir(world, px, py, pz, 0, 1);
        int xSpan = airNegX + 1 + airPosX;
        int zSpan = airNegZ + 1 + airPosZ;
        context.sendMessage(Message.raw("Air extents: -X=" + airNegX + " +X=" + airPosX +
                " -Z=" + airNegZ + " +Z=" + airPosZ +
                " (spans: X=" + xSpan + " Z=" + zSpan + ")"));

        // Vertical clearance
        int clearance = 0;
        for (int y = py; y < py + 50; y++) {
            BlockType bt = world.getBlockType(px, y, pz);
            if (bt != null && bt.getMaterial() == BlockMaterial.Solid) break;
            clearance++;
        }
        context.sendMessage(Message.raw("Vertical clearance: " + clearance));

        // Floor flatness: count solid blocks at Y-1 in a 5x5 area
        int solidFloor = 0;
        int totalFloor = 0;
        for (int x = px - 2; x <= px + 2; x++) {
            for (int z = pz - 2; z <= pz + 2; z++) {
                totalFloor++;
                BlockType bt = world.getBlockType(x, py - 1, z);
                if (bt != null && bt.getMaterial() == BlockMaterial.Solid) solidFloor++;
            }
        }
        context.sendMessage(Message.raw("Floor solidity (5x5): " + solidFloor + "/" + totalFloor));

        // Wall proximity: count solid blocks in a ring at distance 3-5
        int wallBlocks = 0;
        int ringTotal = 0;
        for (int x = px - 5; x <= px + 5; x++) {
            for (int z = pz - 5; z <= pz + 5; z++) {
                int dx = Math.abs(x - px);
                int dz = Math.abs(z - pz);
                if (dx < 3 && dz < 3) continue;
                ringTotal++;
                for (int y = py; y < py + 4; y++) {
                    BlockType bt = world.getBlockType(x, y, z);
                    if (bt != null && bt.getMaterial() == BlockMaterial.Solid) {
                        wallBlocks++;
                        break;
                    }
                }
            }
        }
        context.sendMessage(Message.raw("Wall density (ring 3-5): " + wallBlocks + "/" + ringTotal));

        // Shortest wall direction
        String shortestDir;
        int shortestDist;
        if (airNegX <= airPosX && airNegX <= airNegZ && airNegX <= airPosZ) {
            shortestDir = "-X"; shortestDist = airNegX;
        } else if (airPosX <= airNegZ && airPosX <= airPosZ) {
            shortestDir = "+X"; shortestDist = airPosX;
        } else if (airNegZ <= airPosZ) {
            shortestDir = "-Z"; shortestDist = airNegZ;
        } else {
            shortestDir = "+Z"; shortestDist = airPosZ;
        }
        context.sendMessage(Message.raw("Nearest wall: " + shortestDir + " (" + shortestDist + " blocks)"));

        // Fluid check: reject if standing in lava/water
        BlockType footBlock = world.getBlockType(px, py, pz);
        boolean inFluid = footBlock != null && footBlock.getId() != null
                && footBlock.getId().startsWith("Fluid_");
        if (inFluid) {
            context.sendMessage(Message.raw("Verdict: REJECTED (submerged in fluid)"));
            return;
        }

        // Tunnel score summary
        // Key signals: wall proximity, wall density (enclosure), clearance, minimum width
        int minAir = Math.min(Math.min(airNegX, airPosX), Math.min(airNegZ, airPosZ));
        int minSpan = Math.min(xSpan, zSpan);
        int maxSpan = Math.max(xSpan, zSpan);
        double wallDensity = ringTotal > 0 ? (double) wallBlocks / ringTotal : 0;
        String verdict;
        if (minAir <= 3 && maxSpan >= 5 && minSpan >= 3 && wallDensity >= 0.5 && clearance >= 4) {
            verdict = "GOOD tunnel-mouth candidate";
        } else if (minAir <= 6 && maxSpan >= 3 && minSpan >= 3 && wallDensity >= 0.3 && clearance >= 3) {
            verdict = "OK candidate";
        } else {
            verdict = "Poor candidate (too open, too narrow, or low enclosure)";
        }
        context.sendMessage(Message.raw("Verdict: " + verdict));
    }

    private void executePasteHere(CommandContext context, World world, Vector3d playerPos,
                                  Store<EntityStore> store) {
        int px = (int) Math.floor(playerPos.getX());
        int py = (int) Math.floor(playerPos.getY());
        int pz = (int) Math.floor(playerPos.getZ());

        context.sendMessage(Message.raw("Pasting Nat20/tree1 with anchor at (" + px + ", " + py + ", " + pz + ")"));

        String prefabKey = "Nat20/tree1";
        Path prefabPath = Nat20PrefabPath.resolve(prefabKey);
        if (prefabPath == null) {
            context.sendMessage(Message.raw("Prefab not found: " + prefabKey));
            return;
        }
        context.sendMessage(Message.raw("Loaded from: " + prefabPath.getFileName()));

        IPrefabBuffer buffer;
        try {
            buffer = PrefabBufferUtil.getCached(prefabPath);
        } catch (Exception e) {
            context.sendMessage(Message.raw("Failed to load buffer: " + e.getMessage()));
            return;
        }

        int anchorX = buffer.getAnchorX();
        int anchorY = buffer.getAnchorY();
        int anchorZ = buffer.getAnchorZ();
        context.sendMessage(Message.raw("Buffer anchor: (" + anchorX + ", " + anchorY + ", " + anchorZ + ")"));
        context.sendMessage(Message.raw("Buffer bounds: X[" + buffer.getMinX() + ".." + buffer.getMaxX() +
                "] Y[" + buffer.getMinY() + ".." + buffer.getMaxY() +
                "] Z[" + buffer.getMinZ() + ".." + buffer.getMaxZ() + "]"));

        // Test: pass player position directly (PrefabUtil.paste may treat pos as anchor)
        Vector3i pastePos = new Vector3i(px, py, pz);
        context.sendMessage(Message.raw("Paste position (raw feet): (" + px + ", " + py + ", " + pz + ")"));
        context.sendMessage(Message.raw("If paste uses pos as anchor, entrance should be at your feet"));

        // Pre-load chunks
        int minCX = ChunkUtil.chunkCoordinate(pastePos.getX() + buffer.getMinX());
        int minCZ = ChunkUtil.chunkCoordinate(pastePos.getZ() + buffer.getMinZ());
        int maxCX = ChunkUtil.chunkCoordinate(pastePos.getX() + buffer.getMaxX());
        int maxCZ = ChunkUtil.chunkCoordinate(pastePos.getZ() + buffer.getMaxZ());

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                futures.add(world.getNonTickingChunkAsync(ChunkUtil.indexChunk(cx, cz)));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        context.sendMessage(Message.raw("Chunk loading failed."));
                        return;
                    }
                    world.execute(() -> {
                        try {
                            PrefabUtil.paste(buffer, world, pastePos, Rotation.None, true, new Random(), 0, store);
                            context.sendMessage(Message.raw("Pasted at (" + pastePos.getX() + ", " + pastePos.getY() + ", " + pastePos.getZ() + "). Check if anchor aligns with your feet."));
                        } catch (Exception e) {
                            context.sendMessage(Message.raw("Paste failed: " + e.getMessage()));
                        }
                    });
                });
    }

    private void executeTestPOI(CommandContext context, World world, Vector3d playerPos,
                                 Store<EntityStore> store, Ref<EntityStore> playerRef) {
        CaveVoidRegistry registry = Natural20.getInstance().getCaveVoidRegistry();
        int px = (int) playerPos.getX();
        int pz = (int) playerPos.getZ();

        CaveVoidRecord nearest = registry.findAnyVoid(px, pz);
        if (nearest == null) {
            context.sendMessage(Message.raw("No unclaimed cave voids found. Run 'scan' first."));
            return;
        }

        context.sendMessage(Message.raw("Testing POI flow at void (" +
            nearest.getCenterX() + ", " + nearest.getCenterY() + ", " + nearest.getCenterZ() + ")"));

        // Pick a random mob role from the pool
        String[] testRoles = {"Trork_Warrior", "Skeleton", "Zombie", "Spider", "Goblin_Scrapper"};
        String mobRole = testRoles[new Random().nextInt(testRoles.length)];
        int mobCount = 4;

        Natural20.getInstance().getStructurePlacer()
            .placeAtVoid(world, nearest, store)
            .whenComplete((placed, error) -> {
                if (error != null || placed == null) {
                    context.sendMessage(Message.raw("Prefab placement failed."));
                    return;
                }
                Vector3i entrance = placed.anchorWorld();
                context.sendMessage(Message.raw("Prefab placed at (" +
                    entrance.getX() + ", " + entrance.getY() + ", " + entrance.getZ() + ")"));
                context.sendMessage(Message.raw("Spawning " + mobCount + "x " + mobRole + "..."));

                // Spawn mobs on the world thread
                world.execute(() -> {
                    Natural20.getInstance().getPOIPopulationListener().spawnMobs(
                        world, mobRole, mobCount,
                        entrance.getX(), entrance.getY(), entrance.getZ());
                    context.sendMessage(Message.raw("Done! /tp " +
                        entrance.getX() + " " + entrance.getY() + " " + entrance.getZ()));
                });
            });

        registry.claimVoid(nearest, "test");
    }

    private int scanAir(World world, int x, int y, int z, int dx, int dz) {
        int count = 0;
        int cx = x + dx;
        int cz = z + dz;
        while (count < 50) {
            BlockType bt = world.getBlockType(cx, y, cz);
            if (bt != null && bt.getMaterial() == BlockMaterial.Solid) break;
            count++;
            cx += dx;
            cz += dz;
        }
        return count;
    }
}
