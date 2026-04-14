package com.chonbosmods.quest.model;

/**
 * Runtime-assigned quest difficulty tier. Drives reward tier range, XP amount,
 * mob iLvl / boss behavior, and objective count multipliers. Loaded from
 * {@code src/main/resources/quests/difficulty/*.json} by {@link
 * com.chonbosmods.quest.QuestDifficultyRegistry}.
 *
 * <p>Templates never reference a difficulty directly. {@link
 * com.chonbosmods.quest.QuestGenerator} picks one (randomly for MVP) at
 * generation time and applies its values throughout.
 *
 * <p>Tier fields use vanilla Hytale quality ids ("Common", "Uncommon", "Rare",
 * "Epic", "Legendary") verbatim, per project convention documented in the
 * item-quality-rendering memory.
 */
public record DifficultyConfig(
    String id,
    int xpAmount,
    String rewardTierMin,
    String rewardTierMax,
    int rewardIlvl,
    int mobIlvl,
    boolean mobBoss,
    int bossIlvlOffset,
    double mobCountMultiplier,
    double gatherCountMultiplier
) {}
