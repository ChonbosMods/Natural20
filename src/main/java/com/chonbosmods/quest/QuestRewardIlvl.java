package com.chonbosmods.quest;

/**
 * Pure-function helpers for quest reward + encounter ilvl computation.
 *
 * <p>Reward formula (per-player, computed at phase turn-in):
 * {@code clamp(playerLevel, areaLevel - 5, areaLevel) + ilvlBonus}, then clamped to [1, 45].
 *
 * <p>Encounter formula (shared per quest, world-consistent):
 * {@code areaLevel + ilvlBonus}, clamped to [1, 45].
 *
 * <p>See design doc: {@code docs/plans/2026-04-25-quest-reward-encounter-scaling-design.md}.
 */
public final class QuestRewardIlvl {

    private static final int MIN_ILVL = 1;
    private static final int MAX_ILVL = 45;
    private static final int CLAMP_BANDWIDTH = 5;

    private QuestRewardIlvl() {}

    /**
     * Reward ilvl for a single player completing a quest phase.
     *
     * @param playerLevel  accepter's current mlvl
     * @param areaLevel    areaLevel snapshot at quest generation
     * @param ilvlBonus    difficulty bonus (Easy=0, Medium=2, Hard=5)
     */
    public static int reward(int playerLevel, int areaLevel, int ilvlBonus) {
        int lo = Math.max(MIN_ILVL, areaLevel - CLAMP_BANDWIDTH);
        int hi = Math.max(lo, areaLevel);
        int clamped = Math.max(lo, Math.min(hi, playerLevel));
        return Math.max(MIN_ILVL, Math.min(MAX_ILVL, clamped + ilvlBonus));
    }

    /**
     * Encounter (mob) ilvl for a quest. World-consistent: depends only on area + difficulty.
     */
    public static int encounter(int areaLevel, int ilvlBonus) {
        return Math.max(MIN_ILVL, Math.min(MAX_ILVL, areaLevel + ilvlBonus));
    }
}
