package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.prefab.Nat20PrefabPaster;
import com.chonbosmods.prefab.PlacedMarkers;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Assemble a piece-mode settlement: enumerate a pool of prefab "pieces",
 * randomly select {@code [minPieces, maxPieces]} of them, spread them around
 * a shared center with a minimum bounding-box gap, paste each with a random
 * cardinal rotation, and return a single merged {@link PlacedMarkers} whose
 * spawn lists aggregate every placed piece's markers.
 */
public final class SettlementPieceAssembler {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PieceAssembler");

    /** Max position-candidate retries before giving up on an individual piece. */
    private static final int MAX_RETRIES_PER_PIECE = 30;

    /** Four cardinal rotations used for per-piece yaw. */
    private static final Rotation[] CARDINAL_ROTATIONS = {
        Rotation.None, Rotation.Ninety, Rotation.OneEighty, Rotation.TwoSeventy
    };

    private SettlementPieceAssembler() {}

    public static CompletableFuture<PlacedMarkers> assemble(
            World world, Vector3i center, PiecePlacement config,
            ComponentAccessor<EntityStore> store, Random rng) {

        List<Path> pool = enumeratePool(config.poolCategory());
        if (pool.isEmpty()) {
            LOGGER.atWarning().log("Piece pool '%s' is empty; cannot assemble", config.poolCategory());
            return CompletableFuture.completedFuture(null);
        }

        int targetCount = config.minPieces()
                + rng.nextInt(config.maxPieces() - config.minPieces() + 1);
        LOGGER.atInfo().log("Assembling %d pieces from '%s' (pool size=%d) at %s",
            targetCount, config.poolCategory(), pool.size(), center);

        List<Placement> placements = planPlacements(targetCount, pool, center, config, rng);
        if (placements.isEmpty()) {
            LOGGER.atWarning().log("No pieces fit at %s with spacing=%d radius=%d",
                center, config.minSpacing(), config.outerRadius());
            return CompletableFuture.completedFuture(null);
        }

        // Paste all pieces; await all, then merge markers.
        List<CompletableFuture<PlacedMarkers>> futures = new ArrayList<>(placements.size());
        for (Placement p : placements) {
            futures.add(Nat20PrefabPaster.paste(p.buffer, world, p.anchor, p.yaw, rng, store));
        }
        return CompletableFuture
            .allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> merge(center, futures));
    }

    private static List<Path> enumeratePool(String poolCategory) {
        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile == null) return List.of();
        Path candidate = pluginFile;
        for (int i = 0; i < 5; i++) {
            List<Path> found = scanDir(candidate.resolve("assets").resolve("Server")
                .resolve("Prefabs").resolve("Nat20").resolve(poolCategory));
            if (!found.isEmpty()) return found;
            found = scanDir(candidate.resolve("Server").resolve("Prefabs")
                .resolve("Nat20").resolve(poolCategory));
            if (!found.isEmpty()) return found;
            candidate = candidate.getParent();
            if (candidate == null) break;
        }
        return List.of();
    }

    private static List<Path> scanDir(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".prefab.json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan pool dir %s", dir);
            return List.of();
        }
    }

    private static List<Placement> planPlacements(int targetCount, List<Path> pool,
                                                   Vector3i center, PiecePlacement config,
                                                   Random rng) {
        List<Placement> placements = new ArrayList<>(targetCount);
        List<Bounds> placed = new ArrayList<>(targetCount);

        for (int i = 0; i < targetCount; i++) {
            Path pick = pool.get(rng.nextInt(pool.size()));
            IPrefabBuffer buffer;
            try {
                buffer = PrefabBufferUtil.getCached(pick);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to load piece %s", pick);
                continue;
            }

            Placement chosen = null;
            for (int retry = 0; retry < MAX_RETRIES_PER_PIECE; retry++) {
                Rotation yaw = CARDINAL_ROTATIONS[rng.nextInt(CARDINAL_ROTATIONS.length)];
                Vector3i candidateAnchor = randomAnchor(center, config.outerRadius(), rng);
                Bounds candidateBounds = rotatedBoundsAt(buffer, candidateAnchor, yaw);

                if (fits(candidateBounds, placed, config.minSpacing())) {
                    placed.add(candidateBounds);
                    chosen = new Placement(pick, buffer, candidateAnchor, yaw);
                    break;
                }
            }

            if (chosen == null) {
                LOGGER.atFine().log("Gave up on piece %d/%d after %d retries",
                    i + 1, targetCount, MAX_RETRIES_PER_PIECE);
                continue;
            }
            placements.add(chosen);
        }
        return placements;
    }

    private static Vector3i randomAnchor(Vector3i center, int outerRadius, Random rng) {
        double angle = rng.nextDouble() * 2 * Math.PI;
        double dist = rng.nextDouble() * outerRadius;
        int dx = (int) Math.round(Math.cos(angle) * dist);
        int dz = (int) Math.round(Math.sin(angle) * dist);
        return new Vector3i(center.getX() + dx, center.getY(), center.getZ() + dz);
    }

    /**
     * Axis-aligned XZ bounding box of a rotated prefab pasted at {@code anchor}.
     * Rotates the four corners of the prefab-local XZ box, picks min/max in world coords.
     * Y is ignored (piece spacing only cares about ground footprint).
     */
    private static Bounds rotatedBoundsAt(IPrefabBuffer buffer, Vector3i anchor, Rotation yaw) {
        int[] cornersX = {buffer.getMinX(), buffer.getMinX(), buffer.getMaxX(), buffer.getMaxX()};
        int[] cornersZ = {buffer.getMinZ(), buffer.getMaxZ(), buffer.getMinZ(), buffer.getMaxZ()};
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int k = 0; k < 4; k++) {
            int[] r = rotateXZ(cornersX[k], cornersZ[k], yaw);
            int wx = anchor.getX() + r[0];
            int wz = anchor.getZ() + r[1];
            minX = Math.min(minX, wx); maxX = Math.max(maxX, wx);
            minZ = Math.min(minZ, wz); maxZ = Math.max(maxZ, wz);
        }
        return new Bounds(minX, maxX, minZ, maxZ);
    }

    /** Rotate an (x, z) integer pair by one of the four Hytale cardinal Rotations. */
    private static int[] rotateXZ(int x, int z, Rotation yaw) {
        return switch (yaw) {
            case None       -> new int[]{ x,  z};
            case Ninety     -> new int[]{-z,  x};
            case OneEighty  -> new int[]{-x, -z};
            case TwoSeventy -> new int[]{ z, -x};
        };
    }

    private static boolean fits(Bounds candidate, List<Bounds> placed, int minSpacing) {
        for (Bounds b : placed) {
            if (candidate.overlapsWithinSpacing(b, minSpacing)) return false;
        }
        return true;
    }

    private static PlacedMarkers merge(Vector3i center, List<CompletableFuture<PlacedMarkers>> futures) {
        List<Vector3d> npcs = new ArrayList<>();
        List<Vector3d> mobGroups = new ArrayList<>();
        List<Vector3d> chests = new ArrayList<>();
        int pasted = 0;
        for (CompletableFuture<PlacedMarkers> f : futures) {
            PlacedMarkers pm = f.getNow(null);
            if (pm == null) continue;
            pasted++;
            npcs.addAll(pm.npcSpawnsWorld());
            mobGroups.addAll(pm.mobGroupSpawnsWorld());
            chests.addAll(pm.chestSpawnsWorld());
        }
        if (pasted == 0) {
            LOGGER.atWarning().log("No pieces pasted successfully at %s", center);
            return null;
        }
        LOGGER.atInfo().log("Piece settlement at %s: pieces=%d npcs=%d mobGroups=%d chests=%d",
            center, pasted, npcs.size(), mobGroups.size(), chests.size());

        // The merged settlement's anchor is the shared center; direction is
        // arbitrary (pieces face different ways).
        return new PlacedMarkers(
            center,
            new Vector3i(0, 0, 1),
            Collections.unmodifiableList(npcs),
            Collections.unmodifiableList(mobGroups),
            Collections.unmodifiableList(chests)
        );
    }

    private record Placement(Path source, IPrefabBuffer buffer, Vector3i anchor, Rotation yaw) {}

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        boolean overlapsWithinSpacing(Bounds other, int spacing) {
            // Separated if X or Z ranges are at least `spacing` apart.
            if (this.maxX + spacing < other.minX || other.maxX + spacing < this.minX) return false;
            if (this.maxZ + spacing < other.minZ || other.maxZ + spacing < this.minZ) return false;
            return true;
        }
    }
}
