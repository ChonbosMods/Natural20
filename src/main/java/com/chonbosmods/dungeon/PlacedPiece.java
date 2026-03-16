package com.chonbosmods.dungeon;

import com.hypixel.hytale.math.vector.Vector3i;

public record PlacedPiece(
    DungeonPieceVariant variant,
    int gridX, int gridY, int gridZ,
    Vector3i blockOrigin
) {}
