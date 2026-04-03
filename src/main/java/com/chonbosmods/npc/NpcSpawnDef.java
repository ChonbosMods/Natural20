package com.chonbosmods.npc;

import com.hypixel.hytale.math.vector.Vector3f;

public record NpcSpawnDef(
    String role,
    double xOffset,
    double zOffset,
    Vector3f rotation,
    double leashRadius,
    int dispositionMin,
    int dispositionMax
) {
    /** Convenience constructor with default disposition range [40, 80]. */
    public NpcSpawnDef(String role, double xOffset, double zOffset,
                       Vector3f rotation, double leashRadius) {
        this(role, xOffset, zOffset, rotation, leashRadius, 40, 80);
    }
}
