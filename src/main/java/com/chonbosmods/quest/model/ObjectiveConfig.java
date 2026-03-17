package com.chonbosmods.quest.model;

import javax.annotation.Nullable;
import java.util.Random;

public record ObjectiveConfig(
    @Nullable Integer countMin,
    @Nullable Integer countMax,
    @Nullable String locationPreference
) {
    public int rollCount(Random random) {
        int min = countMin != null ? countMin : 1;
        int max = countMax != null ? countMax : min;
        return min + random.nextInt(max - min + 1);
    }
}
