package com.chonbosmods.loot.display;

/**
 * Pre-rendered affix tooltip line. {@code renderedText} is the final string with Hytale
 * color markup already applied; {@code type} (STAT/EFFECT/ABILITY) drives section ordering
 * in {@link Nat20TooltipStringBuilder}.
 */
public record AffixLine(String type, String renderedText) {}
