package com.chonbosmods.npc;

/**
 * Declares a role to spawn inside a settlement, keyed by role name and count.
 * Unlike the older {@code NpcSpawnDef}, this record carries no position : positions
 * come from {@code Nat20_Npc_Spawn} markers painted into the prefab.
 */
public record NpcSpawnRole(String role, int count, double leashRadius, int dispositionMin, int dispositionMax) {

    /** Convenience constructor with default disposition range [40, 80]. */
    public NpcSpawnRole(String role, int count, double leashRadius) {
        this(role, count, leashRadius, 40, 80);
    }
}
