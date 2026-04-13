package com.chonbosmods.loot.mob.naming;

import javax.annotation.Nullable;

/**
 * A single prefix or suffix word entry from the elite name pool.
 *
 * @param word      the word text (prefixes are capitalized, suffixes are lowercase)
 * @param category  semantic category for the word (e.g. "darkness", "violence", "body_parts")
 * @param minRarity minimum rarity tier that can use this word
 * @param maxRarity maximum rarity tier that can use this word (null = no ceiling)
 * @param source    origin dataset ("d2" or "hytale_ext")
 */
public record MobNameWord(
    String word,
    String category,
    MobNameRarity minRarity,
    @Nullable MobNameRarity maxRarity,
    String source
) {}
