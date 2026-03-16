package com.chonbosmods.dungeon;

import com.hypixel.hytale.math.vector.Vector3i;
import java.util.List;

public record DungeonGeneratorConfig(
    int minPieces,
    int maxPieces,
    Vector3i worldAnchor,
    List<String> guaranteeTags
) {
    public DungeonGeneratorConfig {
        if (minPieces < 1) throw new IllegalArgumentException("minPieces must be >= 1");
        if (maxPieces < minPieces) throw new IllegalArgumentException("maxPieces must be >= minPieces");
    }
}
