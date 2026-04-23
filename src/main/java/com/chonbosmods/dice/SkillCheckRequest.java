package com.chonbosmods.dice;

import com.chonbosmods.stats.Skill;
import com.chonbosmods.stats.Stat;

import javax.annotation.Nullable;

public record SkillCheckRequest(Skill skill, @Nullable Stat stat, int dc, RollMode mode) {}
