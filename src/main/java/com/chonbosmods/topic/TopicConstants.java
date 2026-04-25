package com.chonbosmods.topic;

import java.util.*;

/**
 * Shared constants for the topic/dialogue system. Referenced by both
 * TopicGenerator (runtime) and DialogueDryRun (offline preview).
 */
public final class TopicConstants {

    private TopicConstants() {}

    // --- Role-based topic budgets ---
    // Two tiers: Guard (terse, watch-themed) and Villager (everything else).
    // Total topics per NPC (smalltalk + injected quest topic) must never exceed
    // MAX_TOTAL_TOPICS_PER_NPC. TopicGenerator caps smalltalk budgets against
    // this when an NPC is selected as a quest bearer.
    public static final int MAX_TOTAL_TOPICS_PER_NPC = 3;

    public static final int GUARD_MIN_TOPICS = 0;
    public static final int GUARD_MAX_TOPICS = 1;
    public static final int VILLAGER_MIN_TOPICS = 2;
    public static final int VILLAGER_MAX_TOPICS = 3;

    public static final Set<String> GUARD_ROLES = Set.of("Guard");

    // --- Topic label system ---
    // 6 broad Morrowind-style labels shown in the dialogue UI.
    // Each label maps to multiple internal pool categories.

    public static final String LABEL_LOCAL   = "Local";
    public static final String LABEL_PEOPLE  = "People";
    public static final String LABEL_RUMORS  = "Rumors";
    public static final String LABEL_ADVICE  = "Advice";
    public static final String LABEL_WORK    = "Work";
    public static final String LABEL_HISTORY = "History";

    public static final Map<String, List<String>> LABEL_CATEGORIES = Map.of(
        LABEL_LOCAL,   List.of("poi_awareness", "settlement_pride", "creature_complaints", "mundane_daily_life"),
        LABEL_PEOPLE,  List.of("npc_opinions", "family_talk"),
        LABEL_RUMORS,  List.of("distant_rumors", "travelers_and_trade", "night_watch"),
        LABEL_ADVICE,  List.of("folk_wisdom", "idle_musings", "food_and_meals"),
        LABEL_WORK,    List.of("work_life"),
        LABEL_HISTORY, List.of("old_times")
    );

    public static final List<String> ALL_LABELS = List.of(
        LABEL_LOCAL, LABEL_PEOPLE, LABEL_RUMORS, LABEL_ADVICE, LABEL_WORK, LABEL_HISTORY
    );

    // Guard's narrow watch-themed pool. Villagers (everything non-Guard) sample
    // from ALL_LABELS uniformly, so each villager-tier NPC gets a random subset
    // of 2-3 distinct labels and no role inflates a single label.
    public static final List<String> GUARD_LABELS = List.of(
        LABEL_LOCAL, LABEL_RUMORS, LABEL_PEOPLE
    );

    // Category -> parent topic label (reverse lookup, computed from LABEL_CATEGORIES)
    public static final Map<String, String> CATEGORY_LABEL;
    static {
        Map<String, String> map = new HashMap<>();
        for (var entry : LABEL_CATEGORIES.entrySet()) {
            for (String cat : entry.getValue()) {
                map.put(cat, entry.getKey());
            }
        }
        CATEGORY_LABEL = Collections.unmodifiableMap(map);
    }
}
