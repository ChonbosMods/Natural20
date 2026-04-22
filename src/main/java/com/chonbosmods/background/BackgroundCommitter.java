package com.chonbosmods.background;

import com.chonbosmods.data.Nat20PlayerData;

/**
 * Applies a Background's effects to a player's persistent data.
 *
 * <p>This class is the single point of commit for "the player has chosen their
 * character background." Downstream tasks (Task 2.3) add kit-grant and the full
 * {@code commit(...)} orchestrator. This task implements stat-application only;
 * it is pure data-mutation and fully unit-testable without any ECS/world context.
 */
public final class BackgroundCommitter {

    private BackgroundCommitter() {}

    /**
     * Adds +3 to the background's primary stat and +3 to its secondary, and
     * marks the player's {@code firstJoinSeen} flag so the auto-trigger in
     * {@code PlayerReadyEvent} won't re-fire on subsequent logins.
     *
     * <p>Stats are added on top of whatever value currently exists (not reset).
     * Callers are responsible for gating against double-application; see the
     * first-join flag in {@link Nat20PlayerData#isFirstJoinSeen()}.
     */
    public static void applyStats(Nat20PlayerData data, Background background) {
        int[] stats = data.getStats().clone();
        stats[background.primary().index()] += 3;
        stats[background.secondary().index()] += 3;
        data.setStats(stats);
        data.setFirstJoinSeen(true);
    }
}
