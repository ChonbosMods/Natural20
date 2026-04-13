package com.chonbosmods.loot.mob.naming;

/**
 * A single prefix or suffix word entry from the elite name pool.
 *
 * @param word      the word text (prefixes are capitalized, suffixes are lowercase)
 * @param category  semantic category for the word (e.g. "darkness", "violence", "body_parts")
 * @param minRarity minimum rarity tier that can use this word
 * @param source    origin dataset ("d2" or "hytale_ext")
 */
public record MobNameWord(String word, String category, MobNameRarity minRarity, String source) {}
