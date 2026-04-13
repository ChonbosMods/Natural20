package com.chonbosmods.loot.mob.naming;

/**
 * A title/appellation entry from the elite name pool (e.g. "the Destroyer", "the Wraith").
 *
 * @param title     the full title string including "the" (e.g. "the Hammer")
 * @param category  semantic category (e.g. "weapon", "role", "occult")
 * @param minRarity minimum rarity tier that can use this appellation
 * @param source    origin dataset ("d2" or "hytale_ext")
 */
public record MobNameAppellation(String title, String category, MobNameRarity minRarity, String source) {}
