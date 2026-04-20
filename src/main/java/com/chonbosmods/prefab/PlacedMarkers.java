package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

import java.util.List;

/**
 * Result of placing a Nat20 prefab. All coordinates are world-space and already
 * account for the paste rotation applied to the source {@link MarkerScan}.
 *
 * @param anchorWorld           world position of the anchor block (integer cell).
 * @param directionVectorWorld  cardinal unit vector, post-rotation (horizontal).
 * @param npcSpawnsWorld        world positions for NPC spawns (block-centered doubles).
 * @param mobGroupSpawnsWorld   world positions for hostile mob-group anchors.
 * @param chestSpawnsWorld      world positions for fetch-quest chest candidates.
 */
public record PlacedMarkers(
    Vector3i anchorWorld,
    Vector3i directionVectorWorld,
    List<Vector3d> npcSpawnsWorld,
    List<Vector3d> mobGroupSpawnsWorld,
    List<Vector3d> chestSpawnsWorld
) {}
