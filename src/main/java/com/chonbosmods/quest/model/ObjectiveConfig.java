package com.chonbosmods.quest.model;

import com.chonbosmods.quest.ObjectiveType;

import javax.annotation.Nullable;
import java.util.Random;

public record ObjectiveConfig(
    @Nullable ObjectiveType type,
    @Nullable Integer countMin,
    @Nullable Integer countMax,
    @Nullable String locationPreference
) {
    /** Legacy 3-arg constructor for existing code that doesn't specify type. */
    public ObjectiveConfig(@Nullable Integer countMin, @Nullable Integer countMax,
                           @Nullable String locationPreference) {
        this(null, countMin, countMax, locationPreference);
    }

    public int rollCount(Random random) {
        int min = countMin != null ? countMin : 1;
        int max = countMax != null ? countMax : min;
        return min + random.nextInt(max - min + 1);
    }
}
