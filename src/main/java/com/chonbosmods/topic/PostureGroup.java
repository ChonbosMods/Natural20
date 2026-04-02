package com.chonbosmods.topic;

import java.util.List;
import java.util.Set;

public record PostureGroup(
    String name,
    int warmth,
    int trust,
    Set<String> valenceAffinity,
    int dispositionModifier,
    List<String> prompts
) {}
