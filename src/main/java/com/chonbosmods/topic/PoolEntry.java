package com.chonbosmods.topic;

import com.chonbosmods.dialogue.ValenceType;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A coherent triplet pool entry: intro + matching details + matching reactions.
 * Optionally includes a stat check with pass/fail text authored alongside the content.
 */
public record PoolEntry(
    int id,
    String intro,
    List<String> details,
    List<String> reactions,
    @Nullable StatCheck statCheck,
    ValenceType valence
) {
    public record StatCheck(
        String pass,
        String fail
    ) {}
}
