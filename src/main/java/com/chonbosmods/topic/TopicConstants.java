package com.chonbosmods.topic;

import java.util.*;

/**
 * Shared constants for the topic/dialogue system. Referenced by both
 * TopicGenerator (runtime) and DialogueDryRun (offline preview).
 */
public final class TopicConstants {

    private TopicConstants() {}

    // --- Role-based topic budgets ---
    public static final int SOCIAL_MIN_TOPICS = 2;
    public static final int SOCIAL_MAX_TOPICS = 4;
    public static final int FUNCTIONAL_MIN_TOPICS = 0;
    public static final int FUNCTIONAL_MAX_TOPICS = 2;
    public static final Set<String> SOCIAL_ROLES = Set.of(
        "TavernKeeper", "ArtisanAlchemist", "ArtisanBlacksmith", "ArtisanCook", "Traveler"
    );

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

    // Role -> preferred labels in priority order. NPCs get 2-3 from their list.
    public static final Map<String, List<String>> ROLE_LABELS = Map.of(
        "Guard",            List.of(LABEL_LOCAL, LABEL_RUMORS, LABEL_PEOPLE),
        "TavernKeeper",     List.of(LABEL_PEOPLE, LABEL_RUMORS, LABEL_LOCAL),
        "ArtisanBlacksmith", List.of(LABEL_WORK, LABEL_LOCAL, LABEL_PEOPLE),
        "ArtisanCook",      List.of(LABEL_WORK, LABEL_PEOPLE, LABEL_ADVICE),
        "ArtisanAlchemist", List.of(LABEL_WORK, LABEL_ADVICE, LABEL_LOCAL),
        "Villager",         List.of(LABEL_LOCAL, LABEL_PEOPLE, LABEL_HISTORY),
        "Traveler",         List.of(LABEL_RUMORS, LABEL_HISTORY, LABEL_ADVICE)
    );

    public static final List<String> DEFAULT_LABELS = List.of(LABEL_LOCAL, LABEL_PEOPLE, LABEL_RUMORS);

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
