package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Walks an {@link IPrefabBuffer} once, extracting the anchor, direction, and
 * spawn-marker positions into a {@link MarkerScan}.
 *
 * <p>Callers must have invoked {@link Nat20PrefabConstants#resolve()} before
 * using this scanner; the marker-ID static fields on
 * {@link Nat20PrefabConstants} must be populated or nothing will match.
 *
 * <p>The scanner enforces two invariants and will throw
 * {@link IllegalArgumentException} if violated:
 * <ul>
 *     <li>Exactly one {@code Nat20_Anchor} block.</li>
 *     <li>Exactly one {@code Nat20_Direction} block.</li>
 * </ul>
 * Non-marker blocks are silently ignored, and NPC / mob-group / chest markers
 * may appear zero or more times each.
 */
public final class Nat20PrefabMarkerScanner {

    private Nat20PrefabMarkerScanner() {
        // utility class
    }

    /**
     * Scan a prefab buffer for Nat20 marker blocks and return the collected
     * local-space positions.
     *
     * <p>When the anchor or direction marker is missing or duplicated, this
     * method throws {@link IllegalArgumentException} directly. When the
     * direction block coincides horizontally with the anchor (i.e. their
     * offset has zero X and Z components), the throw propagates from
     * {@link DirectionVector#snapToCardinal} via delegation rather than from
     * an explicit check in this scanner.
     *
     * @throws IllegalArgumentException if the anchor or direction marker is
     *         missing or duplicated, or (via delegation to
     *         {@link DirectionVector#snapToCardinal}) if the direction block
     *         has no horizontal offset from the anchor.
     */
    public static MarkerScan scan(IPrefabBuffer buffer) {
        List<Vector3i> anchors = new ArrayList<>();
        List<Vector3i> directions = new ArrayList<>();
        List<Vector3i> npcs = new ArrayList<>();
        List<Vector3i> mobGroups = new ArrayList<>();
        List<Vector3i> chests = new ArrayList<>();
        int[] structureMinX = { Integer.MAX_VALUE };
        int[] structureMaxX = { Integer.MIN_VALUE };
        int[] structureMinZ = { Integer.MAX_VALUE };
        int[] structureMaxZ = { Integer.MIN_VALUE };

        buffer.forEach(
                IPrefabBuffer.iterateAllColumns(),
                (x, y, z, blockId, holder, supportValue, rotation, filler, call, fluidId, fluidLevel) -> {
                    if (blockId == Nat20PrefabConstants.anchorId) {
                        anchors.add(new Vector3i(x, y, z));
                    } else if (blockId == Nat20PrefabConstants.directionId) {
                        directions.add(new Vector3i(x, y, z));
                    } else if (blockId == Nat20PrefabConstants.npcSpawnId) {
                        npcs.add(new Vector3i(x, y, z));
                    } else if (blockId == Nat20PrefabConstants.mobGroupSpawnId) {
                        mobGroups.add(new Vector3i(x, y, z));
                    } else if (blockId == Nat20PrefabConstants.chestSpawnId) {
                        chests.add(new Vector3i(x, y, z));
                    } else if (blockId != 0 && !Nat20PrefabConstants.stripIds().contains(blockId)) {
                        // Count toward the structural footprint only if it's a real block
                        // (not air, not a Nat20 marker, not a vanilla authoring/spawner block).
                        if (x < structureMinX[0]) structureMinX[0] = x;
                        if (x > structureMaxX[0]) structureMaxX[0] = x;
                        if (z < structureMinZ[0]) structureMinZ[0] = z;
                        if (z > structureMaxZ[0]) structureMaxZ[0] = z;
                    }
                },
                (x, z, entities, t) -> {
                    // entities: no-op
                },
                (x, y, z, path, fitHeightmap, inheritSeed, inheritHeightCondition, weights, rot, t) -> {
                    // child prefabs: no-op
                },
                new PrefabBufferCall(new Random(), PrefabRotation.ROTATION_0)
        );

        if (anchors.size() != 1) {
            if (anchors.isEmpty()) {
                throw new IllegalArgumentException(
                        "Prefab must have exactly one Nat20_Anchor block; found 0");
            }
            throw new IllegalArgumentException(
                    "Prefab must have exactly one Nat20_Anchor block; found "
                            + anchors.size() + " at " + anchors);
        }
        if (directions.size() != 1) {
            if (directions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Prefab must have exactly one Nat20_Direction block; found 0");
            }
            throw new IllegalArgumentException(
                    "Prefab must have exactly one Nat20_Direction block; found "
                            + directions.size() + " at " + directions);
        }

        Vector3i anchor = anchors.get(0);
        Vector3i direction = directions.get(0);
        Vector3i directionVector = DirectionVector.snapToCardinal(
                direction.getX() - anchor.getX(),
                direction.getY() - anchor.getY(),
                direction.getZ() - anchor.getZ());

        // Structure bounds fall back to a 1x1 at the anchor if the prefab has no
        // structural blocks at all (only markers). Won't happen for real prefabs;
        // defensive so downstream bound math never sees Integer.MAX_VALUE.
        int sMinX = structureMinX[0] == Integer.MAX_VALUE ? anchor.getX() : structureMinX[0];
        int sMaxX = structureMaxX[0] == Integer.MIN_VALUE ? anchor.getX() : structureMaxX[0];
        int sMinZ = structureMinZ[0] == Integer.MAX_VALUE ? anchor.getZ() : structureMinZ[0];
        int sMaxZ = structureMaxZ[0] == Integer.MIN_VALUE ? anchor.getZ() : structureMaxZ[0];

        return new MarkerScan(anchor, direction, directionVector, npcs, mobGroups, chests,
                sMinX, sMaxX, sMinZ, sMaxZ);
    }
}
