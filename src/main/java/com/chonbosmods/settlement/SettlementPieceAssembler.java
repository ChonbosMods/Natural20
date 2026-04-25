package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.prefab.MarkerScan;
import com.chonbosmods.prefab.Nat20PrefabMarkerScanner;
import com.chonbosmods.prefab.Nat20PrefabPaster;
import com.chonbosmods.prefab.PlacedMarkers;
import com.chonbosmods.world.Nat20HeightmapSampler;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
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
import java.util.concurrent.TimeUnit;
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
    private static final int MAX_RETRIES_PER_PIECE = 60;

    /** Extra pieces planned above target to absorb paste-time rejections (terrain). */
    private static final int OVERPROVISION = 2;

    /** Default floor: fewer than this many pieces actually pasted → settlement
     *  is not worth keeping. Tutorial uses an override (usually 1) so its
     *  always-placed spawn settlement never aborts. */
    public static final int DEFAULT_MIN_PASTED_PIECES = 4;

    /** Four cardinal rotations used for per-piece yaw. */
    private static final Rotation[] CARDINAL_ROTATIONS = {
        Rotation.None, Rotation.Ninety, Rotation.OneEighty, Rotation.TwoSeventy
    };

    private SettlementPieceAssembler() {}

    public static CompletableFuture<PlacedMarkers> assemble(
            World world, Vector3i center, PiecePlacement config,
            ComponentAccessor<EntityStore> store, Random rng) {
        return assemble(world, center, config, store, rng, DEFAULT_MIN_PASTED_PIECES);
    }

    /**
     * Assemble with an explicit minimum-pasted-pieces threshold. Use the
     * default {@link #DEFAULT_MIN_PASTED_PIECES} for regular procedural
     * settlements; the tutorial spawn settlement passes a lower value (usually
     * 1) so it never aborts when terrain is uncooperative.
     */
    public static CompletableFuture<PlacedMarkers> assemble(
            World world, Vector3i center, PiecePlacement config,
            ComponentAccessor<EntityStore> store, Random rng,
            int minPastedPieces) {

        List<Path> pool = com.chonbosmods.prefab.Nat20PrefabPath.enumeratePool(config.poolCategory());
        if (pool.isEmpty()) {
            LOGGER.atWarning().log("Piece pool '%s' is empty; cannot assemble", config.poolCategory());
            return CompletableFuture.completedFuture(null);
        }

        int targetCount = config.minPieces()
                + rng.nextInt(config.maxPieces() - config.minPieces() + 1);
        int planCount = targetCount + OVERPROVISION;
        LOGGER.atInfo().log("Assembling %d pieces (plan %d) from '%s' (pool size=%d) at %s",
            targetCount, planCount, config.poolCategory(), pool.size(), center);

        List<Placement> placements = planPlacements(planCount, pool, center, config, rng);
        if (placements.isEmpty()) {
            LOGGER.atWarning().log("No pieces fit at %s with spacing=%d radius=%d",
                center, config.minSpacing(), config.outerRadius());
            return CompletableFuture.completedFuture(null);
        }

        // Paste all pieces; await all, then merge markers. Each piece is first
        // grounded to the terrain heightmap (Mode.MIN so it sits on its lowest
        // corner), then pasted at the corrected Y. Pieces on too-steep or
        // no-ground terrain complete with null and are dropped by merge().
        List<CompletableFuture<PlacedMarkers>> futures = new ArrayList<>(placements.size());
        for (Placement p : placements) {
            futures.add(pasteGrounded(p, world, rng, store));
        }
        return CompletableFuture
            .allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> merge(center, futures, minPastedPieces));
    }

    /**
     * Preload the chunks covered by the rotated piece footprint, sample the
     * terrain surface with {@link Nat20HeightmapSampler.Mode#MIN}, and paste
     * at the anchor overridden to the sampled Y. Returns a future completing
     * with {@code null} if no ground is found or the slope exceeds the
     * sampler's default threshold (the piece is silently dropped by merge()).
     */
    private static CompletableFuture<PlacedMarkers> pasteGrounded(
            Placement placement, World world, Random rng,
            ComponentAccessor<EntityStore> store) {
        Bounds b = rotatedStructureBoundsAt(placement.scan, placement.anchor, placement.yaw);
        int halfX = Math.max(placement.anchor.getX() - b.minX, b.maxX - placement.anchor.getX());
        int halfZ = Math.max(placement.anchor.getZ() - b.minZ, b.maxZ - placement.anchor.getZ());

        // Preload the chunks the sampler's 5 probes will read. The probe corners
        // sit at the edges of the rotated footprint, so preload the footprint's
        // chunk range directly (Nat20PrefabPaster.paste will preload again but
        // that's a no-op once chunks are already non-ticking-loaded).
        int minCX = ChunkUtil.chunkCoordinate(b.minX);
        int maxCX = ChunkUtil.chunkCoordinate(b.maxX);
        int minCZ = ChunkUtil.chunkCoordinate(b.minZ);
        int maxCZ = ChunkUtil.chunkCoordinate(b.maxZ);
        List<CompletableFuture<?>> chunkFutures = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                chunkFutures.add(world.getNonTickingChunkAsync(ChunkUtil.indexChunk(cx, cz)));
            }
        }

        CompletableFuture<PlacedMarkers> out = new CompletableFuture<>();
        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
            .orTimeout(30, TimeUnit.SECONDS)
            .whenComplete((ignored, err) -> {
                if (err != null) {
                    LOGGER.atWarning().withCause(err).log(
                        "Piece grounding: chunk preload failed at (%d, %d); skipping",
                        placement.anchor.getX(), placement.anchor.getZ());
                    out.complete(null);
                    return;
                }
                world.execute(() -> {
                    Nat20HeightmapSampler.SampleResult ground = Nat20HeightmapSampler.sample(
                        world, placement.anchor.getX(), placement.anchor.getZ(),
                        halfX, halfZ, Nat20HeightmapSampler.Mode.MEDIAN);
                    if (ground.y() <= 0) {
                        LOGGER.atFine().log(
                            "Piece grounding: no ground at (%d, %d); skipping",
                            placement.anchor.getX(), placement.anchor.getZ());
                        out.complete(null);
                        return;
                    }
                    if (ground.tooSteep()) {
                        LOGGER.atFine().log(
                            "Piece grounding: slope %d too steep at (%d, %d); skipping",
                            ground.slopeDelta(), placement.anchor.getX(), placement.anchor.getZ());
                        out.complete(null);
                        return;
                    }
                    if (ground.tooWet()) {
                        LOGGER.atFine().log(
                            "Piece grounding: submerged (depth=%d) at (%d, %d); skipping",
                            ground.maxSubmergedDepth(), placement.anchor.getX(), placement.anchor.getZ());
                        out.complete(null);
                        return;
                    }
                    Vector3i groundedAnchor = new Vector3i(
                        placement.anchor.getX(), ground.y(), placement.anchor.getZ());
                    // Nat20PrefabPaster.paste validates markers synchronously and throws
                    // IllegalArgumentException if the prefab is missing a required anchor
                    // or direction marker. Catch here so one malformed piece doesn't
                    // escape the world.execute lambda and tank the whole task queue entry.
                    CompletableFuture<PlacedMarkers> pasteFuture;
                    try {
                        pasteFuture = Nat20PrefabPaster.paste(placement.buffer, world,
                            groundedAnchor, placement.yaw, rng, store);
                    } catch (RuntimeException scanErr) {
                        LOGGER.atSevere().withCause(scanErr).log(
                            "Piece paste rejected malformed prefab '%s'; skipping",
                            placement.source);
                        out.complete(null);
                        return;
                    }
                    pasteFuture.whenComplete((placed, pasteErr) -> {
                        if (pasteErr != null) {
                            LOGGER.atSevere().withCause(pasteErr).log(
                                "Piece paste failed at (%d, %d, %d) source=%s",
                                groundedAnchor.getX(), groundedAnchor.getY(),
                                groundedAnchor.getZ(), placement.source);
                            out.complete(null);
                            return;
                        }
                        out.complete(placed);
                    });
                });
            });
        return out;
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

            // Scan markers up front: (1) catch malformed prefabs at plan time instead
            // of paste time, (2) get structural bounds for edge-to-edge spacing that
            // excludes stray marker blocks that extend past the visible structure.
            MarkerScan scan;
            try {
                scan = Nat20PrefabMarkerScanner.scan(buffer);
            } catch (RuntimeException e) {
                LOGGER.atWarning().log("Skipping malformed piece '%s': %s", pick, e.getMessage());
                continue;
            }

            Placement chosen = null;
            for (int retry = 0; retry < MAX_RETRIES_PER_PIECE; retry++) {
                Rotation yaw = CARDINAL_ROTATIONS[rng.nextInt(CARDINAL_ROTATIONS.length)];
                Vector3i candidateAnchor = randomAnchor(center, config.outerRadius(), rng);
                Bounds candidateBounds = rotatedStructureBoundsAt(scan, candidateAnchor, yaw);

                if (fits(candidateBounds, placed, config.minSpacing())) {
                    placed.add(candidateBounds);
                    chosen = new Placement(pick, buffer, scan, candidateAnchor, yaw);
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
     * Axis-aligned XZ bounding box of a rotated prefab's STRUCTURAL footprint pasted
     * at {@code anchor}. Uses {@link MarkerScan#structureMinX()} etc. so the bounds
     * reflect only the visible structure (non-marker, non-empty blocks), not
     * free-floating marker blocks that would otherwise inflate the AABB.
     * Y is ignored (piece spacing only cares about ground footprint).
     */
    private static Bounds rotatedStructureBoundsAt(MarkerScan scan, Vector3i anchor, Rotation yaw) {
        int[] cornersX = {scan.structureMinX(), scan.structureMinX(), scan.structureMaxX(), scan.structureMaxX()};
        int[] cornersZ = {scan.structureMinZ(), scan.structureMaxZ(), scan.structureMinZ(), scan.structureMaxZ()};
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

    private static PlacedMarkers merge(Vector3i center, List<CompletableFuture<PlacedMarkers>> futures,
                                       int minPastedPieces) {
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
        if (pasted < minPastedPieces) {
            LOGGER.atWarning().log(
                "Only %d/%d pieces pasted at %s (min required: %d); abandoning settlement. "
                    + "Any pasted pieces remain in the world as orphan ruins.",
                pasted, futures.size(), center, minPastedPieces);
            return null;
        }
        LOGGER.atInfo().log("Piece settlement at %s: pieces=%d/%d npcs=%d mobGroups=%d chests=%d",
            center, pasted, futures.size(), npcs.size(), mobGroups.size(), chests.size());

        // The merged settlement's anchor is the shared center; direction is
        // arbitrary (pieces face different ways). Translation is meaningless for
        // a multi-piece merge (each piece had its own), so report zero; callers
        // that need per-piece bounds must use the per-piece PlacedMarkers, not
        // this aggregate.
        return new PlacedMarkers(
            center,
            new Vector3i(0, 0, 1),
            new Vector3i(0, 0, 0),
            Collections.unmodifiableList(npcs),
            Collections.unmodifiableList(mobGroups),
            Collections.unmodifiableList(chests)
        );
    }

    private record Placement(Path source, IPrefabBuffer buffer, MarkerScan scan,
                             Vector3i anchor, Rotation yaw) {}

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        boolean overlapsWithinSpacing(Bounds other, int spacing) {
            // Separated if X or Z ranges are at least `spacing` apart.
            if (this.maxX + spacing < other.minX || other.maxX + spacing < this.minX) return false;
            if (this.maxZ + spacing < other.minZ || other.maxZ + spacing < this.minZ) return false;
            return true;
        }
    }
}
