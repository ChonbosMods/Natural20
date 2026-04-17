package com.chonbosmods.quest.poi;

/**
 * Rolled 50/50 once at first-spawn of a POI quest's mob group.
 * Frozen for the life of the quest; determines how kill credit is dispatched.
 */
public enum PoiGroupDirection {
    /** Any group member kill credits the objective. requiredCount == championCount + 1. */
    KILL_COUNT,
    /** Only the boss kill credits. requiredCount == 1. */
    KILL_BOSS
}
