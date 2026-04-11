package com.chonbosmods.combat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared dirty flag for score bonus recalculation. When a player's ability
 * scores change or equipment changes, mark them dirty so the bonus system
 * recalculates on the next tick.
 */
public final class Nat20ScoreDirtyFlag {

    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    private Nat20ScoreDirtyFlag() {}

    public static void markDirty(UUID uuid) {
        DIRTY.add(uuid);
    }

    public static boolean isDirty(UUID uuid) {
        return DIRTY.contains(uuid);
    }

    public static void clearDirty(UUID uuid) {
        DIRTY.remove(uuid);
    }

    public static void removePlayer(UUID uuid) {
        DIRTY.remove(uuid);
    }
}
