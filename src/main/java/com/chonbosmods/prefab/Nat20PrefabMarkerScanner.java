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
                    }
                    // other blocks: ignored
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

        return new MarkerScan(anchor, direction, directionVector, npcs, mobGroups, chests);
    }
}
