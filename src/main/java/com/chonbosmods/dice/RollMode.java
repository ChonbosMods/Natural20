package com.chonbosmods.dice;

/**
 * Controls how a d20 is rolled for a skill check.
 *
 * <ul>
 *   <li>{@link #NORMAL}: single d20.</li>
 *   <li>{@link #ADVANTAGE}: roll two d20s, keep the higher.</li>
 *   <li>{@link #DISADVANTAGE}: roll two d20s, keep the lower.</li>
 * </ul>
 */
public enum RollMode {
    NORMAL,
    ADVANTAGE,
    DISADVANTAGE
}
