package com.chonbosmods.cave;

import com.chonbosmods.Natural20;
import com.chonbosmods.prefab.MarkerScan;
import com.chonbosmods.prefab.Nat20PrefabMarkerScanner;
import com.chonbosmods.prefab.Nat20PrefabPaster;
import com.chonbosmods.prefab.PlacedMarkers;
import com.chonbosmods.prefab.YawAlignment;
import com.chonbosmods.world.Nat20HeightmapSampler;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Places a prefab structure adjacent to a cave void and carves a raw connecting tunnel.
 */
public class UndergroundStructurePlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|CavePlacer");
    // Both cave and surface POI paths resolve to the marker-bearing test prefab
    // under hostile_poi/. Swap for dedicated per-biome / per-size prefabs once
    // more author content lands.
    private static final String TEST_PREFAB_KEY = "Nat20/hostile_poi/testStructure";
    private static final String SURFACE_FALLBACK_PREFAB_KEY = "Nat20/hostile_poi/testStructure";

    /**
     * Place a structure adjacent to the given cave void and carve a connecting tunnel.
     *
     * @param world     the world to place into
     * @param voidRecord the cave void to place adjacent to
     * @param store     component accessor for entity persistence
     * @return a future that completes with the placed marker positions (anchor + mob-group spawns + etc.),
     *         or null on failure
     */
    public CompletableFuture<PlacedMarkers> placeAtVoid(World world, CaveVoidRecord voidRecord,
                                                       Store<EntityStore> store) {
        CompletableFuture<PlacedMarkers> result = new CompletableFuture<>();

        // 1. Score floor positions for tunnel-mouth placement
        List<int[]> floorPositions = voidRecord.getFloorPositions();
        if (floorPositions == null || floorPositions.isEmpty()) {
            LOGGER.atWarning().log("No floor positions in void at (%d, %d, %d)",
                    voidRecord.getCenterX(), voidRecord.getCenterY(), voidRecord.getCenterZ());
            result.complete(null);
            return result;
        }

        int cx = voidRecord.getCenterX();
        int cy = voidRecord.getCenterY();
        int cz = voidRecord.getCenterZ();

        int[] bestFloor = null;
        int bestScore = -1;
        String bestWallDir = null;

        for (int[] fp : floorPositions) {
            int x = fp[0], y = fp[1], z = fp[2];

            // Skip fluid positions
            BlockType footBlock = world.getBlockType(x, y, z);
            if (footBlock != null && footBlock.getId() != null
                    && footBlock.getId().startsWith("Fluid_")) continue;

            // Ledge filter: check that below the floor is thick solid, not air
            // A real cave floor has solid for several blocks below; a ceiling ledge has air
            int solidBelow = 0;
            for (int dy = y - 1; dy >= y - 5; dy--) {
                BlockType bt = world.getBlockType(x, dy, z);
                if (bt != null && bt.getMaterial() == BlockMaterial.Solid) solidBelow++;
            }
            if (solidBelow < 3) continue;

            // Vertical clearance
            int clearance = 0;
            for (int dy = y; dy < y + 50; dy++) {
                BlockType bt = world.getBlockType(x, dy, z);
                if (bt != null && bt.getMaterial() == BlockMaterial.Solid) break;
                clearance++;
            }
            if (clearance < 4) continue;

            // Air extents in each cardinal direction
            int airNegX = scanAir(world, x, y, z, -1, 0);
            int airPosX = scanAir(world, x, y, z, 1, 0);
            int airNegZ = scanAir(world, x, y, z, 0, -1);
            int airPosZ = scanAir(world, x, y, z, 0, 1);

            int xSpan = airNegX + 1 + airPosX;
            int zSpan = airNegZ + 1 + airPosZ;
            int minSpan = Math.min(xSpan, zSpan);
            int maxSpan = Math.max(xSpan, zSpan);
            int minAir = Math.min(Math.min(airNegX, airPosX), Math.min(airNegZ, airPosZ));

            // Reject: too narrow (crevice) or too open (center of chamber)
            if (minSpan < 3 || maxSpan < 5 || minAir > 6) continue;

            // Wall density in ring 3-5
            int wallBlocks = 0;
            int ringTotal = 0;
            for (int rx = x - 5; rx <= x + 5; rx++) {
                for (int rz = z - 5; rz <= z + 5; rz++) {
                    int dx = Math.abs(rx - x);
                    int dz = Math.abs(rz - z);
                    if (dx < 3 && dz < 3) continue;
                    ringTotal++;
                    for (int ry = y; ry < y + 4; ry++) {
                        BlockType bt = world.getBlockType(rx, ry, rz);
                        if (bt != null && bt.getMaterial() == BlockMaterial.Solid) {
                            wallBlocks++;
                            break;
                        }
                    }
                }
            }
            double wallDensity = ringTotal > 0 ? (double) wallBlocks / ringTotal : 0;
            if (wallDensity < 0.3) continue;

            // Score: prefer close wall + high enclosure + good clearance
            // Lower minAir = closer to wall = better
            int score = (int) (wallDensity * 100) + clearance - minAir * 5;

            // Determine which direction the nearest wall is
            String wallDir;
            if (airNegX <= airPosX && airNegX <= airNegZ && airNegX <= airPosZ) wallDir = "-X";
            else if (airPosX <= airNegZ && airPosX <= airPosZ) wallDir = "+X";
            else if (airNegZ <= airPosZ) wallDir = "-Z";
            else wallDir = "+Z";

            if (score > bestScore) {
                bestScore = score;
                bestFloor = fp;
                bestWallDir = wallDir;
            }
        }

        if (bestFloor == null) {
            LOGGER.atWarning().log("No suitable tunnel-mouth floor in void at (%d, %d, %d)",
                    cx, cy, cz);
            result.complete(null);
            return result;
        }

        int floorX = bestFloor[0];
        int floorY = bestFloor[1];
        int floorZ = bestFloor[2];

        // 2. Load the prefab buffer
        Path prefabPath = findPrefabPath();
        if (prefabPath == null) {
            LOGGER.atSevere().log("Prefab not found: %s", TEST_PREFAB_KEY);
            result.complete(null);
            return result;
        }

        IPrefabBuffer buffer;
        try {
            buffer = PrefabBufferUtil.getCached(prefabPath);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load prefab buffer: %s", prefabPath);
            result.complete(null);
            return result;
        }

        // 3. Compute desired world-facing direction from the nearest-wall axis.
        //    The entrance should face AWAY from the nearest wall, i.e. into the cave.
        //    Wall on -X -> entrance faces +X; wall on +X -> -X; wall on +Z -> -Z; wall on -Z -> +Z.
        Vector3i wantedWorldDir = switch (bestWallDir) {
            case "-X" -> new Vector3i( 1, 0,  0);
            case "+X" -> new Vector3i(-1, 0,  0);
            case "+Z" -> new Vector3i( 0, 0, -1);
            default   -> new Vector3i( 0, 0,  1); // "-Z" -> entrance faces +Z
        };

        // Scan the prefab up-front so we can align its authored direction vector to the
        // wanted world direction via YawAlignment. Note: the paster will scan again
        // internally; that's cheap and keeps the paster API self-contained.
        MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer);
        Rotation rotation = YawAlignment.computeYawToAlign(scan.directionVector(), wantedWorldDir);

        LOGGER.atFine().log("Selected tunnel-mouth at (%d, %d, %d) score=%d wallDir=%s rotation=%s for void at (%d, %d, %d)",
                floorX, floorY, floorZ, bestScore, bestWallDir, rotation, cx, cy, cz);

        // 4. Delegate to the paster. It handles chunk preload + tick defer + filtered paste,
        //    and returns world-space marker positions.
        Nat20PrefabPaster.paste(buffer, world, new Vector3i(floorX, floorY, floorZ),
                        rotation, new Random(), store)
                .whenComplete((placed, error) -> {
                    if (error != null || placed == null) {
                        if (error != null) {
                            LOGGER.atSevere().withCause(error).log(
                                    "Cave POI paste failed at void (%d, %d, %d)", cx, cy, cz);
                        }
                        result.complete(null);
                        return;
                    }
                    LOGGER.atFine().log("Cave POI placed at (%d, %d, %d) rotation=%s",
                            floorX, floorY, floorZ, rotation);
                    result.complete(placed);
                });

        return result;
    }

    /**
     * Find the prefab file path. Tries asset pack lookup first, then falls back to walking
     * up the filesystem from the plugin file to find assets/Server/Prefabs/.
     */
    private Path findPrefabPath() {
        return findPrefabPath(TEST_PREFAB_KEY);
    }

    private Path findSurfaceFallbackPrefabPath() {
        return findPrefabPath(SURFACE_FALLBACK_PREFAB_KEY);
    }

    private Path findPrefabPath(String prefabKey) {
        // Try asset pack lookup first
        Path assetPath = PrefabStore.get().findAssetPrefabPath(prefabKey);
        if (assetPath != null) {
            return assetPath;
        }

        // Fall back: resolve from plugin file path (dev mode)
        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile != null) {
            Path candidate = pluginFile;
            for (int i = 0; i < 4; i++) {
                Path assetsDir = candidate.resolve("assets").resolve("Server").resolve("Prefabs")
                        .resolve(prefabKey + ".prefab.json");
                if (Files.exists(assetsDir)) {
                    LOGGER.atFine().log("Found prefab via fallback path: %s", assetsDir);
                    return assetsDir;
                }
                Path directDir = candidate.resolve("Server").resolve("Prefabs")
                        .resolve(prefabKey + ".prefab.json");
                if (Files.exists(directDir)) {
                    LOGGER.atFine().log("Found prefab via direct path: %s", directDir);
                    return directDir;
                }
                candidate = candidate.getParent();
                if (candidate == null) break;
            }
        }

        return null;
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

    /**
     * Place the surface-fallback prefab at surface level at the given x/z position.
     * Finds the surface Y by scanning downward, then pastes with no rotation via the
     * shared {@link Nat20PrefabPaster} pipeline.
     *
     * @return a future that completes with the placed marker positions, or null on failure
     */
    public CompletableFuture<PlacedMarkers> placeAtSurface(World world, int targetX, int targetZ,
                                                          Store<EntityStore> store) {
        CompletableFuture<PlacedMarkers> result = new CompletableFuture<>();

        Path prefabPath = findSurfaceFallbackPrefabPath();
        if (prefabPath == null) {
            LOGGER.atSevere().log("Surface placement: surfaceFallbackPrefab not found: %s", SURFACE_FALLBACK_PREFAB_KEY);
            result.complete(null);
            return result;
        }

        IPrefabBuffer buffer;
        try {
            buffer = PrefabBufferUtil.getCached(prefabPath);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Surface placement: failed to load prefab buffer");
            result.complete(null);
            return result;
        }

        // Pre-load the 3x3 chunk grid around the target so the sampler's corner probes
        // (which may fall in neighboring chunks) all have loaded heightmaps.
        int centerCX = ChunkUtil.chunkCoordinate(targetX);
        int centerCZ = ChunkUtil.chunkCoordinate(targetZ);
        java.util.List<java.util.concurrent.CompletableFuture<?>> chunkFutures = new java.util.ArrayList<>();
        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
                chunkFutures.add(world.getNonTickingChunkAsync(
                    ChunkUtil.indexChunk(centerCX + dcx, centerCZ + dcz)));
            }
        }
        java.util.concurrent.CompletableFuture.allOf(chunkFutures.toArray(new java.util.concurrent.CompletableFuture[0]))
            .orTimeout(30, TimeUnit.SECONDS)
            .whenComplete((ignored, err) -> {
                if (err != null) {
                    LOGGER.atWarning().withCause(err).log("Surface placement: chunk load failed");
                    result.complete(null);
                    return;
                }
                world.execute(() -> {
                    int halfX = Math.max(Math.abs(buffer.getMinX()), Math.abs(buffer.getMaxX()));
                    int halfZ = Math.max(Math.abs(buffer.getMinZ()), Math.abs(buffer.getMaxZ()));
                    Nat20HeightmapSampler.SampleResult sample = Nat20HeightmapSampler.sample(
                        world, targetX, targetZ, halfX, halfZ,
                        Nat20HeightmapSampler.Mode.ENTRY_ANCHOR);

                    if (sample.y() <= 0) {
                        LOGGER.atWarning().log("Surface placement: no ground found at (%d, %d)", targetX, targetZ);
                        result.complete(null);
                        return;
                    }
                    if (sample.tooSteep()) {
                        LOGGER.atFine().log("Surface placement: slope %d too steep at (%d, %d); skipping",
                            sample.slopeDelta(), targetX, targetZ);
                        result.complete(null);
                        return;
                    }

                    final int surfaceY = sample.y();
                    Vector3i pastePos = new Vector3i(targetX, surfaceY, targetZ);

                    // Delegate to the paster. It handles chunk preload + tick defer + filtered paste.
                    // Surface prefabs don't rotate, so pass Rotation.None.
                    Nat20PrefabPaster.paste(buffer, world, pastePos,
                                    Rotation.None, new Random(), store)
                            .whenComplete((placed, error) -> {
                                if (error != null || placed == null) {
                                    if (error != null) {
                                        LOGGER.atSevere().withCause(error).log(
                                                "Surface placement: paste failed at (%d, %d, %d)",
                                                targetX, surfaceY, targetZ);
                                    }
                                    result.complete(null);
                                    return;
                                }
                                LOGGER.atFine().log("Surface POI placed at (%d, %d, %d)",
                                        targetX, surfaceY, targetZ);
                                result.complete(placed);
                            });
                });
            });

        return result;
    }

}
