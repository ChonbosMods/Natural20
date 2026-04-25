package com.chonbosmods.prefab;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for placing a Nat20 prefab into a live world. Composes the
 * {@link Nat20PrefabMarkerScanner}, the {@link Nat20FilteredBuffer}, and
 * vanilla {@link PrefabUtil#paste} so callers get:
 *
 * <ul>
 *     <li>Marker scan + invariant enforcement on the source buffer.</li>
 *     <li>Automatic pre-load of every chunk the rotated prefab footprint
 *         touches, as non-ticking, with a 30 s timeout.</li>
 *     <li>A {@value #DEFER_TICKS}-tick defer to let the world settle before
 *         pasting (matches the cave placer pattern).</li>
 *     <li>Paste through {@link Nat20FilteredBuffer} so the Empty passthrough
 *         flip and {@code Nat20_Force_Empty} carve both apply.</li>
 *     <li>A {@link PlacedMarkers} result with every marker position already
 *         rotated and translated into world coordinates.</li>
 * </ul>
 *
 * <p>This class is pure composition: it cannot be unit-tested cleanly because
 * {@link PrefabUtil#paste} needs a live {@link World}. Smoke testing happens
 * at the integration layer.
 */
public final class Nat20PrefabPaster {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Paster");
    private static final int DEFER_TICKS = 5;

    private Nat20PrefabPaster() {
        // utility class
    }

    /**
     * Scan the prefab for markers, pre-load chunks as non-ticking, defer
     * {@value #DEFER_TICKS} ticks, then paste via
     * {@link PrefabUtil#paste} through a {@link Nat20FilteredBuffer}. Returns
     * a future completing with world-space marker positions.
     *
     * <p>The prefab is placed such that after rotation its
     * {@code Nat20_Anchor} block lands on {@code desiredAnchorWorld}. Direction
     * is a pure vector: rotation applies, translation does not.
     *
     * @param buffer             the loaded prefab
     * @param world              the world to paste into
     * @param desiredAnchorWorld world cell where the rotated anchor should land
     * @param yaw                rotation applied to the paste and to all
     *                           returned marker positions
     * @param random             RNG for vanilla paste operations
     * @param store              component accessor for entity persistence
     * @return future resolving to {@link PlacedMarkers} on success, or
     *         {@code null} on chunk-load timeout or paste failure. Marker-scan
     *         validation errors propagate synchronously as
     *         {@link IllegalArgumentException}.
     */
    public static CompletableFuture<PlacedMarkers> paste(
            IPrefabBuffer buffer,
            World world,
            Vector3i desiredAnchorWorld,
            Rotation yaw,
            Random random,
            ComponentAccessor<EntityStore> store) {
        // 1. Scan for markers. Throws synchronously if the prefab is malformed.
        MarkerScan scan = Nat20PrefabMarkerScanner.scan(buffer);

        // 2. Compute the world translation so the rotated anchor lands on
        //    desiredAnchorWorld. PrefabUtil.paste's `position` argument is the
        //    world cell that prefab-local (0,0,0) maps to after rotation (see
        //    PrefabBuffer.forEach at line 698: blockConsumer.accept(rotatedX,
        //    y, rotatedZ, ...); PrefabUtil.paste then adds `position` to those
        //    rotated coords). So translation = desiredAnchor - rotate(anchorLocal).
        PrefabRotation rot = PrefabRotation.fromRotation(yaw);
        Vector3i rotatedAnchor = rotateInt(rot, scan.anchorLocal());
        Vector3i translation = new Vector3i(
                desiredAnchorWorld.getX() - rotatedAnchor.getX(),
                desiredAnchorWorld.getY() - rotatedAnchor.getY(),
                desiredAnchorWorld.getZ() - rotatedAnchor.getZ());

        // 3. Pre-load every chunk the rotated footprint touches, as non-ticking.
        //    IPrefabBuffer exposes rotation-aware min/max accessors, so we don't
        //    need to rotate corners ourselves.
        int minX = buffer.getMinX(rot) + translation.getX();
        int maxX = buffer.getMaxX(rot) + translation.getX();
        int minZ = buffer.getMinZ(rot) + translation.getZ();
        int maxZ = buffer.getMaxZ(rot) + translation.getZ();
        int minCX = ChunkUtil.chunkCoordinate(minX);
        int maxCX = ChunkUtil.chunkCoordinate(maxX);
        int minCZ = ChunkUtil.chunkCoordinate(minZ);
        int maxCZ = ChunkUtil.chunkCoordinate(maxZ);

        List<CompletableFuture<?>> chunkFutures = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                chunkFutures.add(world.getNonTickingChunkAsync(ChunkUtil.indexChunk(cx, cz)));
            }
        }

        LOGGER.atFine().log(
                "Nat20PrefabPaster preloading %d chunks for rotated footprint x=[%d,%d] z=[%d,%d] rot=%s",
                chunkFutures.size(), minX, maxX, minZ, maxZ, yaw);

        // 4. After chunks load + defer N ticks, paste through the filter buffer
        //    and compute world marker positions.
        CompletableFuture<PlacedMarkers> result = new CompletableFuture<>();
        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.atSevere().withCause(error).log(
                                "Chunk preload failed for Nat20 prefab paste at (%d, %d, %d); aborting",
                                desiredAnchorWorld.getX(), desiredAnchorWorld.getY(), desiredAnchorWorld.getZ());
                        result.complete(null);
                        return;
                    }
                    deferTicks(world, DEFER_TICKS, () -> {
                        try {
                            IPrefabBuffer filtered = new Nat20FilteredBuffer(buffer);
                            PrefabUtil.paste(filtered, world, translation, yaw, true, random, 0, store);
                            LOGGER.atFine().log(
                                    "Nat20 prefab pasted: translation=(%d,%d,%d) rot=%s anchorWorld=(%d,%d,%d)",
                                    translation.getX(), translation.getY(), translation.getZ(), yaw,
                                    desiredAnchorWorld.getX(), desiredAnchorWorld.getY(), desiredAnchorWorld.getZ());
                            result.complete(buildPlacedMarkers(scan, rot, translation));
                        } catch (Exception e) {
                            LOGGER.atSevere().withCause(e).log(
                                    "Nat20 prefab paste failed at (%d, %d, %d)",
                                    desiredAnchorWorld.getX(), desiredAnchorWorld.getY(), desiredAnchorWorld.getZ());
                            result.complete(null);
                        }
                    });
                });

        return result;
    }

    /**
     * Rotate + translate every local marker position into world coordinates.
     * The anchor lands on a block cell (integer); spawn markers come back as
     * block-centered doubles (offset +0.5) so entity spawns sit mid-cell.
     * Direction is a pure vector: rotation applies, translation does not.
     */
    private static PlacedMarkers buildPlacedMarkers(MarkerScan scan, PrefabRotation rot, Vector3i t) {
        Vector3i anchorRot = rotateInt(rot, scan.anchorLocal());
        Vector3i anchorWorld = new Vector3i(
                anchorRot.getX() + t.getX(),
                anchorRot.getY() + t.getY(),
                anchorRot.getZ() + t.getZ());
        Vector3i directionWorld = rotateInt(rot, scan.directionVector());
        return new PlacedMarkers(
                anchorWorld,
                directionWorld,
                t,
                rotateAndTranslate(scan.npcSpawnsLocal(), rot, t),
                rotateAndTranslate(scan.mobGroupSpawnsLocal(), rot, t),
                rotateAndTranslate(scan.chestSpawnsLocal(), rot, t));
    }

    private static List<Vector3d> rotateAndTranslate(List<Vector3i> locals, PrefabRotation rot, Vector3i t) {
        List<Vector3d> out = new ArrayList<>(locals.size());
        for (Vector3i v : locals) {
            Vector3i r = rotateInt(rot, v);
            out.add(new Vector3d(
                    r.getX() + t.getX() + 0.5,
                    r.getY() + t.getY() + 0.5,
                    r.getZ() + t.getZ() + 0.5));
        }
        return out;
    }

    /**
     * Rotate an integer vector about the Y axis via {@link PrefabRotation}. We
     * round through a {@link Vector3d} so the rotation uses the same
     * implementation path as {@link PrefabUtil#paste} does for entity
     * positions, keeping marker placement consistent with block placement.
     */
    private static Vector3i rotateInt(PrefabRotation rot, Vector3i v) {
        Vector3d tmp = new Vector3d(v.getX(), v.getY(), v.getZ());
        rot.rotate(tmp);
        return new Vector3i(
                (int) Math.round(tmp.getX()),
                (int) Math.round(tmp.getY()),
                (int) Math.round(tmp.getZ()));
    }

    private static void deferTicks(World world, int ticks, Runnable action) {
        if (ticks <= 0) {
            world.execute(action);
        } else {
            world.execute(() -> deferTicks(world, ticks - 1, action));
        }
    }
}
