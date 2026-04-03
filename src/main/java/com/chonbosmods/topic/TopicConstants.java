package com.chonbosmods.topic;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // --- Category deck configuration ---
    public static final double RUMOR_RATIO = 0.30;

    public static final List<String> RUMOR_DECK = List.of(
        "poi_awareness", "creature_complaints", "distant_rumors"
    );
    public static final List<String> SMALLTALK_DECK = List.of(
        "mundane_daily_life", "npc_opinions", "settlement_pride"
    );

    // --- Topic labels shown in dialogue UI ---
    public static final Map<String, String> TEMPLATE_LABELS = Map.ofEntries(
        Map.entry("mundane_daily_life", "Daily Life"),
        Map.entry("npc_opinions", "Around Town"),
        Map.entry("settlement_pride", "Life Here"),
        Map.entry("poi_awareness", "Local Happenings"),
        Map.entry("creature_complaints", "Trouble"),
        Map.entry("distant_rumors", "Word from Outside")
    );
}
