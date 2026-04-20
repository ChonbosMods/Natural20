package com.chonbosmods.prefab;

import com.hypixel.hytale.math.vector.Vector3i;

import java.util.List;

/**
 * Result of scanning a prefab buffer for Nat20 marker blocks. All positions are
 * in prefab-local coordinates (pre-rotation, pre-translation).
 *
 * @param anchorLocal         position of the sole {@code Nat20_Anchor} block.
 * @param directionLocal      position of the sole {@code Nat20_Direction} block.
 * @param directionVector     cardinal unit vector pointing from anchor toward direction,
 *                            snapped to the dominant horizontal axis (Y ignored).
 * @param npcSpawnsLocal      positions of every {@code Nat20_Npc_Spawn} marker.
 * @param mobGroupSpawnsLocal positions of every {@code Nat20_Mob_Group_Spawn} marker.
 * @param chestSpawnsLocal    positions of every {@code Nat20_Chest_Spawn} marker.
 */
public record MarkerScan(
    Vector3i anchorLocal,
    Vector3i directionLocal,
    Vector3i directionVector,
    List<Vector3i> npcSpawnsLocal,
    List<Vector3i> mobGroupSpawnsLocal,
    List<Vector3i> chestSpawnsLocal
) {}
