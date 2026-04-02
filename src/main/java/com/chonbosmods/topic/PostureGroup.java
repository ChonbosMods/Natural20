package com.chonbosmods.topic;

import java.util.*;

public record PostureGroup(
    String name,
    int warmth,
    int trust,
    Set<String> valenceAffinity,
    int dispositionModifier,
    Map<String, List<String>> prompts
) {
    /**
     * Select a prompt matching the given valence with fallback chain:
     * requested valence lane -> neutral lane -> any prompt across all lanes.
     */
    public String selectPrompt(String valence, Random random) {
        // Try requested valence lane
        List<String> lane = prompts.get(valence);
        if (lane != null && !lane.isEmpty()) {
            return lane.get(random.nextInt(lane.size()));
        }

        // Fallback: neutral lane
        List<String> neutralLane = prompts.get("neutral");
        if (neutralLane != null && !neutralLane.isEmpty()) {
            return neutralLane.get(random.nextInt(neutralLane.size()));
        }

        // Final fallback: any prompt
        List<String> all = new ArrayList<>();
        for (List<String> l : prompts.values()) all.addAll(l);
        if (all.isEmpty()) return "...";
        return all.get(random.nextInt(all.size()));
    }
}
