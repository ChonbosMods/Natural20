package com.chonbosmods.npc;

import com.hypixel.hytale.math.vector.Vector3f;

public record NpcSpawnDef(
    String role,
    double xOffset,
    double zOffset,
    Vector3f rotation,
    double leashRadius
) {}
