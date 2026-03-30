package com.chonbosmods.topic;

import com.chonbosmods.dialogue.DispositionBracket;
import com.chonbosmods.dialogue.model.DialogueGraph;
import com.chonbosmods.quest.DialogueResolver;
import com.chonbosmods.quest.QuestGenerator;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestPoolRegistry;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.google.common.flogger.FluentLogger;

import java.util.*;

/**
 * Settlement-level orchestrator that generates all topics for every NPC in a settlement.
 * Seeds deterministically from the settlement's cell key so the same topics regenerate
 * on server restart.
 */
public class TopicGenerator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    // Role-based topic budgets: social roles talk more, functional roles talk less
    private static final int SOCIAL_MIN_TOPICS = 4;
    private static final int SOCIAL_MAX_TOPICS = 6;
    private static final int FUNCTIONAL_MIN_TOPICS = 2;
    private static final int FUNCTIONAL_MAX_TOPICS = 4;
    private static final Set<String> SOCIAL_ROLES = Set.of(
        "TavernKeeper", "ArtisanAlchemist", "ArtisanBlacksmith", "ArtisanCook", "Traveler"
    );

    private static final double RUMOR_RATIO = 0.4;
    private static final double QUEST_CHANCE_PER_SUBJECT = 0.40;
    private static final double CLOSER_CHANCE = 0.6;

    // Stratified category decks for subject drawing
    private static final List<String> RUMOR_DECK = List.of(
        "danger", "sighting", "treasure", "corruption", "conflict", "disappearance", "migration", "omen"
    );
    private static final List<String> SMALLTALK_DECK = List.of(
        "trade", "weather", "craftsmanship", "community", "nature", "nostalgia", "curiosity", "festival"
    );


    // Rolling window sizes for pool-draw dedup
    private static final int WINDOW_L0 = 5;   // L0 fragments
    private static final int WINDOW_L1 = 3;   // L1 fragments
    private static final int WINDOW_L2 = 8;   // L2 fragments (settlement-scoped, sized to cover cross-NPC draws)
    private static final int WINDOW_TONE = 4;  // tone openers/closers
    private static final int WINDOW_DROPIN = 2; // drop-ins

    // Disposition-scaled opener chances: hostile NPCs almost always use tone, neutral rarely
    private static final double OPENER_CHANCE_HOSTILE = 0.85;
    private static final double OPENER_CHANCE_UNFRIENDLY = 0.70;
    private static final double OPENER_CHANCE_NEUTRAL = 0.50;
    private static final double OPENER_CHANCE_FRIENDLY = 0.65;
    private static final double OPENER_CHANCE_LOYAL = 0.60;

    private final TopicPoolRegistry topicPool;
    private final TopicTemplateRegistry templateRegistry;
    private final QuestPoolRegistry questPool;
    private final QuestGenerator questGenerator;

    public TopicGenerator(TopicPoolRegistry topicPool, TopicTemplateRegistry templateRegistry,
                          QuestPoolRegistry questPool, QuestGenerator questGenerator) {
        this.topicPool = topicPool;
        this.templateRegistry = templateRegistry;
        this.questPool = questPool;
        this.questGenerator = questGenerator;
    }

    /**
     * Generate dialogue graphs for all NPCs in a settlement.
     *
     * @return map of NPC generated name to their DialogueGraph
     */
    public Map<String, DialogueGraph> generate(SettlementRecord settlement) {
        List<NpcRecord> npcs = settlement.getNpcs();
        if (npcs.isEmpty()) {
            LOGGER.atWarning().log("Settlement %s has no NPCs, skipping topic generation", settlement.getCellKey());
            return Map.of();
        }

        Random random = new Random(settlement.getCellKey().hashCode());
        int npcCount = npcs.size();

        // Step 1: Roll topic budgets per NPC (role-based ranges)
        Map<String, Integer> topicBudgets = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            int min, max;
            if (isSocialRole(npc.getRole())) {
                min = SOCIAL_MIN_TOPICS;
                max = SOCIAL_MAX_TOPICS;
            } else {
                min = FUNCTIONAL_MIN_TOPICS;
                max = FUNCTIONAL_MAX_TOPICS;
            }
            int budget = min + random.nextInt(max - min + 1);
            topicBudgets.put(npc.getGeneratedName(), budget);
        }

        // Step 2: Each NPC draws their own subjects independently
        // Settlement-wide dedup prevents the same subject appearing on multiple NPCs
        Set<String> usedSubjectValues = new LinkedHashSet<>();
        List<SubjectFocus> allSubjects = new ArrayList<>();
        Map<String, List<SubjectFocus>> npcSubjects = new LinkedHashMap<>();
        Map<String, NpcRecord> npcByName = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) npcByName.put(npc.getGeneratedName(), npc);

        for (int npcIdx = 0; npcIdx < npcs.size(); npcIdx++) {
            NpcRecord npc = npcs.get(npcIdx);
            String npcName = npc.getGeneratedName();
            int budget = topicBudgets.get(npcName);

            // Per-NPC deck shuffle (seeded from settlement key + NPC index for determinism)
            Random deckRandom = new Random(settlement.getCellKey().hashCode() ^ ((long) npcIdx * 31));
            int rumorCount = (int) Math.ceil(budget * RUMOR_RATIO);
            int smallTalkCount = budget - rumorCount;

            List<String> rumorDeck = new ArrayList<>(RUMOR_DECK);
            List<String> smalltalkDeck = new ArrayList<>(SMALLTALK_DECK);
            Collections.shuffle(rumorDeck, deckRandom);
            Collections.shuffle(smalltalkDeck, deckRandom);

            List<SubjectFocus> npcTopics = new ArrayList<>();
            int subjectBase = allSubjects.size();

            for (int i = 0; i < rumorCount; i++) {
                String targetCategory = rumorDeck.get(i % rumorDeck.size());
                TopicPoolRegistry.SubjectEntry entry = drawUniqueSubject(targetCategory, usedSubjectValues, random);
                usedSubjectValues.add(entry.value());
                String subjectId = "subj_" + (subjectBase + i) + "_" + sanitize(entry.value());
                SubjectFocus focus = new SubjectFocus(subjectId, entry.value(), entry.plural(), entry.proper(),
                    entry.questEligible(), entry.concrete(), TopicCategory.RUMORS, entry.categories(),
                    entry.poiType(), entry.questAffinities());
                npcTopics.add(focus);
                allSubjects.add(focus);
            }

            for (int i = 0; i < smallTalkCount; i++) {
                String targetCategory = smalltalkDeck.get(i % smalltalkDeck.size());
                TopicPoolRegistry.SubjectEntry entry = drawUniqueSubject(targetCategory, usedSubjectValues, random);
                usedSubjectValues.add(entry.value());
                String subjectId = "subj_" + (subjectBase + rumorCount + i) + "_" + sanitize(entry.value());
                SubjectFocus focus = new SubjectFocus(subjectId, entry.value(), entry.plural(), entry.proper(),
                    entry.questEligible(), entry.concrete(), TopicCategory.SMALLTALK, entry.categories(),
                    entry.poiType(), entry.questAffinities());
                npcTopics.add(focus);
                allSubjects.add(focus);
            }

            npcSubjects.put(npcName, npcTopics);
        }

        // Step 3: Roll quest placement (40% per subject, clamped by settlement size)
        // Build a mapping from subject index to owning NPC for quest generation
        Map<Integer, String> subjectOwners = new LinkedHashMap<>();
        int idx = 0;
        for (var entry : npcSubjects.entrySet()) {
            for (SubjectFocus ignored : entry.getValue()) {
                subjectOwners.put(idx++, entry.getKey());
            }
        }

        int minQuests = Math.max(1, (int) Math.floor(npcCount * 0.25));
        int maxQuests = Math.max(2, (int) Math.floor(npcCount * 0.5));

        List<Integer> questCandidates = new ArrayList<>();
        for (int i = 0; i < allSubjects.size(); i++) {
            if (random.nextDouble() < QUEST_CHANCE_PER_SUBJECT) {
                questCandidates.add(i);
            }
        }

        while (questCandidates.size() < minQuests && questCandidates.size() < allSubjects.size()) {
            int qi = random.nextInt(allSubjects.size());
            if (!questCandidates.contains(qi)) {
                questCandidates.add(qi);
            }
        }

        while (questCandidates.size() > maxQuests) {
            questCandidates.remove(random.nextInt(questCandidates.size()));
        }

        // Quest subjects must use quest-eligible values: swap out non-eligible entries
        for (int qi : questCandidates) {
            SubjectFocus focus = allSubjects.get(qi);
            if (!focus.isQuestEligible()) {
                TopicPoolRegistry.SubjectEntry eligible = topicPool.randomQuestEligibleSubject(random);
                String newId = "subj_" + qi + "_" + sanitize(eligible.value());
                SubjectFocus replacement = new SubjectFocus(newId, eligible.value(), eligible.plural(),
                    eligible.proper(), eligible.questEligible(), eligible.concrete(), focus.getCategory(),
                    eligible.categories(), eligible.poiType(), eligible.questAffinities());
                allSubjects.set(qi, replacement);
                // Update the NPC's subject list too
                String ownerNpc = subjectOwners.get(qi);
                List<SubjectFocus> ownerTopics = npcSubjects.get(ownerNpc);
                ownerTopics.set(ownerTopics.indexOf(focus), replacement);
            }
        }

        // Generate quests: bearer is the NPC who owns the subject
        for (int qi : questCandidates) {
            SubjectFocus focus = allSubjects.get(qi);
            String bearer = subjectOwners.get(qi);
            NpcRecord bearerRecord = npcByName.get(bearer);

            QuestInstance preQuest = questGenerator.generate(
                bearerRecord.getRole(), bearer,
                settlement.getCellKey(),
                bearerRecord.getSpawnX(), bearerRecord.getSpawnZ(),
                Set.of(),
                focus.getQuestAffinities(),
                focus.getPoiType(),
                focus.getSubjectValue()
            );

            if (preQuest != null) {
                bearerRecord.setPreGeneratedQuest(preQuest);
                focus.setQuestBearer(bearer, preQuest.getSituationId());
                focus.setQuestBindings(preQuest.getVariableBindings());
                LOGGER.atInfo().log("  QUEST BEARER: %s (%s) quest=%s | /tp %d %d %d",
                    bearer, bearerRecord.getRole(),
                    preQuest.getSituationId(),
                    (int) bearerRecord.getSpawnX(),
                    (int) bearerRecord.getSpawnY(),
                    (int) bearerRecord.getSpawnZ());
            } else {
                focus.setQuestBearer(null, null);
                LOGGER.atWarning().log("  QUEST BEARER: %s failed to generate quest, demoting to normal topic", bearer);
            }
        }

        // Step 4: Build assignments per NPC, deduplicating resolved labels settlement-wide.
        // If two topics resolve to the same label (e.g. two NPCs both get "The Weather Lately"),
        // the second one is skipped.
        Map<String, List<TopicGraphBuilder.TopicAssignment>> npcAssignments = new LinkedHashMap<>();
        Map<String, LinkedList<String>> poolWindows = new HashMap<>();
        Set<String> usedLabels = new HashSet<>();

        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = new ArrayList<>();
            for (SubjectFocus focus : npcSubjects.get(npcName)) {
                TopicGraphBuilder.TopicAssignment assignment = buildAssignment(focus, npcName, random, poolWindows);
                String resolvedLabel = capitalizeFirst(
                    DialogueResolver.resolve(assignment.labelPattern(), assignment.bindings()));
                if (usedLabels.contains(resolvedLabel)) {
                    continue; // Skip duplicate label
                }
                usedLabels.add(resolvedLabel);
                assignments.add(assignment);
            }
            npcAssignments.put(npcName, assignments);
        }

        // Build final graphs
        Map<String, DialogueGraph> results = new LinkedHashMap<>();
        Set<String> usedDeepeners = new HashSet<>();
        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = npcAssignments.get(npcName);

            String greeting = topicPool.randomGreeting(random);
            String returnGreeting = topicPool.randomReturnGreeting(random);

            TopicGraphBuilder builder = new TopicGraphBuilder(
                npcName, 50, greeting, returnGreeting, assignments, topicPool, random, usedDeepeners
            );
            DialogueGraph graph = builder.build();

            if (graph.validate()) {
                results.put(npcName, graph);
            } else {
                LOGGER.atWarning().log("Generated invalid dialogue graph for NPC %s in settlement %s",
                    npcName, settlement.getCellKey());
            }
        }

        LOGGER.atFine().log("Generated %d dialogue graphs for settlement %s (%d subjects, %d quests)",
            results.size(), settlement.getCellKey(), allSubjects.size(), questCandidates.size());

        // Debug: log per-NPC subject assignments
        for (var npcEntry : npcSubjects.entrySet()) {
            for (SubjectFocus focus : npcEntry.getValue()) {
                String questTag = focus.hasQuest() ? " [QUEST]" : "";
                LOGGER.atFine().log("  %s: '%s' (%s)%s",
                    npcEntry.getKey(), focus.getSubjectValue(), focus.getCategory(), questTag);
            }
        }

        return results;
    }

    /**
     * Build a TopicAssignment for a specific NPC on a specific subject.
     */
    private TopicGraphBuilder.TopicAssignment buildAssignment(SubjectFocus focus, String npcName, Random random,
                                                               Map<String, LinkedList<String>> poolWindows) {
        TopicTemplate template = templateRegistry.randomTemplateForSubject(
            focus.getCategory(), focus.getCategories(), focus.isConcrete(), random);
        boolean isQuestBearer = focus.hasQuest();

        // Pick perspective: quest hook for quest bearer, normal for others
        TopicTemplate.Perspective perspective;
        if (isQuestBearer && !template.questHookPerspectives().isEmpty()) {
            List<TopicTemplate.Perspective> hooks = template.questHookPerspectives();
            perspective = hooks.get(random.nextInt(hooks.size()));
        } else if (!template.perspectives().isEmpty()) {
            List<TopicTemplate.Perspective> normals = template.perspectives();
            perspective = normals.get(random.nextInt(normals.size()));
        } else {
            // Fallback: create a minimal perspective
            perspective = new TopicTemplate.Perspective(
                "I've heard something about {subject_focus}...", List.of(), null, null, null
            );
        }

        // Build variable bindings
        Map<String, String> bindings = buildBindings(focus, npcName, isQuestBearer, template.id(), random, poolWindows);

        return new TopicGraphBuilder.TopicAssignment(
            focus.getSubjectId(),
            template.labelPattern(),
            perspective,
            bindings,
            true,
            isQuestBearer,
            template.skillCheckDef(),
            focus.getCategory()
        );
    }

    /**
     * Build variable bindings for a topic assignment.
     * Uses rolling window dedup to reduce repetition across assignments.
     */
    private Map<String, String> buildBindings(SubjectFocus focus, String npcName,
                                               boolean isQuestBearer, String templateId, Random random,
                                               Map<String, LinkedList<String>> poolWindows) {
        Map<String, String> bindings = new HashMap<>();

        // Subject focus bindings
        String subjectValue = focus.getSubjectValue();
        boolean startsWithThe = subjectValue.toLowerCase().startsWith("the ");
        String bareValue = startsWithThe ? subjectValue.substring(4) : subjectValue;

        bindings.put("subject_focus", subjectValue);
        bindings.put("subject_focus_bare", bareValue);
        bindings.put("subject_focus_is", focus.isPlural() ? "are" : "is");
        bindings.put("subject_focus_has", focus.isPlural() ? "have" : "has");
        bindings.put("subject_focus_was", focus.isPlural() ? "were" : "was");
        bindings.put("subject_focus_the", (focus.isProper() || startsWithThe) ? subjectValue : "the " + subjectValue);
        bindings.put("subject_focus_The", focus.isProper() ? subjectValue
            : startsWithThe ? "T" + subjectValue.substring(1) : "The " + subjectValue);

        // NPC name
        bindings.put("npc_name", npcName);

        // Drop-in pool bindings (window=2)
        bindings.put("time_ref", drawWithWindow("time_ref", poolWindows, WINDOW_DROPIN, () -> topicPool.randomTimeRef(random)));
        bindings.put("direction", drawWithWindow("direction", poolWindows, WINDOW_DROPIN, () -> topicPool.randomDirection(random)));

        // Tone bindings (bracket-filtered by disposition) with disposition-scaled opener chance
        // MVP: disposition is statically bound to neutral (50) at generation time because
        // topic graphs are pre-built per settlement, not per player. Dynamic rebinding
        // would require regenerating tone selections per conversation, which is deferred.
        String bracket = dispositionBracket(50);
        double openerChance = openerChanceForBracket(bracket);
        boolean openerFired = random.nextDouble() < openerChance;
        boolean closerFired = openerFired ? (random.nextDouble() < CLOSER_CHANCE) : true;
        bindings.put("tone_opener", openerFired
            ? drawWithWindow("tone_opener", poolWindows, WINDOW_TONE, () -> topicPool.randomToneOpener(bracket, random))
            : "");
        bindings.put("tone_closer", closerFired
            ? drawWithWindow("tone_closer", poolWindows, WINDOW_TONE, () -> topicPool.randomToneCloser(bracket, random))
            : "");

        // Fragment pool bindings: Layer 0 (window=5)
        bindings.put("creature_sighting", drawWithWindow("creature_sighting", poolWindows, WINDOW_L0, () -> topicPool.randomCreatureSighting(random)));
        bindings.put("strange_event", drawWithWindow("strange_event", poolWindows, WINDOW_L0, () -> topicPool.randomStrangeEvent(random)));
        bindings.put("trade_gossip", drawWithWindow("trade_gossip", poolWindows, WINDOW_L0, () -> topicPool.randomTradeGossip(random)));
        bindings.put("local_complaint", drawWithWindow("local_complaint", poolWindows, WINDOW_L0, () -> topicPool.randomLocalComplaint(random)));
        bindings.put("traveler_news", drawWithWindow("traveler_news", poolWindows, WINDOW_L0, () -> topicPool.randomTravelerNews(random)));

        // Fragment pool bindings: Layer 0 topic-matched (window=5)
        bindings.put("weather_observation", drawWithWindow("weather_observation", poolWindows, WINDOW_L0, () -> topicPool.randomWeatherObservation(random)));
        bindings.put("craft_observation", drawWithWindow("craft_observation", poolWindows, WINDOW_L0, () -> topicPool.randomCraftObservation(random)));
        bindings.put("community_observation", drawWithWindow("community_observation", poolWindows, WINDOW_L0, () -> topicPool.randomCommunityObservation(random)));
        bindings.put("nature_observation", drawWithWindow("nature_observation", poolWindows, WINDOW_L0, () -> topicPool.randomNatureObservation(random)));
        bindings.put("nostalgia_observation", drawWithWindow("nostalgia_observation", poolWindows, WINDOW_L0, () -> topicPool.randomNostalgiaObservation(random)));
        bindings.put("curiosity_observation", drawWithWindow("curiosity_observation", poolWindows, WINDOW_L0, () -> topicPool.randomCuriosityObservation(random)));
        bindings.put("festival_observation", drawWithWindow("festival_observation", poolWindows, WINDOW_L0, () -> topicPool.randomFestivalObservation(random)));
        bindings.put("treasure_rumor", drawWithWindow("treasure_rumor", poolWindows, WINDOW_L0, () -> topicPool.randomTreasureRumor(random)));
        bindings.put("conflict_rumor", drawWithWindow("conflict_rumor", poolWindows, WINDOW_L0, () -> topicPool.randomConflictRumor(random)));

        // Fragment pool bindings: Layer 1 (window=3)
        bindings.put("creature_detail", drawWithWindow("creature_detail", poolWindows, WINDOW_L1, () -> topicPool.randomCreatureDetail(random)));
        bindings.put("event_detail", drawWithWindow("event_detail", poolWindows, WINDOW_L1, () -> topicPool.randomEventDetail(random)));
        bindings.put("trade_detail", drawWithWindow("trade_detail", poolWindows, WINDOW_L1, () -> topicPool.randomTradeDetail(random)));
        bindings.put("location_detail", drawWithWindow("location_detail", poolWindows, WINDOW_L1, () -> topicPool.randomLocationDetail(random)));

        // Fragment pool bindings: Layer 1 topic-matched (window=3)
        bindings.put("weather_detail", drawWithWindow("weather_detail", poolWindows, WINDOW_L1, () -> topicPool.randomWeatherDetail(random)));
        bindings.put("craft_detail", drawWithWindow("craft_detail", poolWindows, WINDOW_L1, () -> topicPool.randomCraftDetail(random)));
        bindings.put("community_detail", drawWithWindow("community_detail", poolWindows, WINDOW_L1, () -> topicPool.randomCommunityDetail(random)));
        bindings.put("nature_detail", drawWithWindow("nature_detail", poolWindows, WINDOW_L1, () -> topicPool.randomNatureDetail(random)));
        bindings.put("nostalgia_detail", drawWithWindow("nostalgia_detail", poolWindows, WINDOW_L1, () -> topicPool.randomNostalgiaDetail(random)));
        bindings.put("curiosity_detail", drawWithWindow("curiosity_detail", poolWindows, WINDOW_L1, () -> topicPool.randomCuriosityDetail(random)));
        bindings.put("festival_detail", drawWithWindow("festival_detail", poolWindows, WINDOW_L1, () -> topicPool.randomFestivalDetail(random)));
        bindings.put("treasure_detail", drawWithWindow("treasure_detail", poolWindows, WINDOW_L1, () -> topicPool.randomTreasureDetail(random)));
        bindings.put("conflict_detail", drawWithWindow("conflict_detail", poolWindows, WINDOW_L1, () -> topicPool.randomConflictDetail(random)));

        // Fragment pool bindings: Layer 2 (window=2)
        // Reaction bracket: rumors + nature + curiosity = intense, all other smalltalk = mild
        String reactionBracket = (focus.getCategory() == TopicCategory.RUMORS
            || "smalltalk_nature".equals(templateId)
            || "smalltalk_curiosity".equals(templateId)) ? "intense" : "mild";
        bindings.put("_reaction_bracket", reactionBracket);
        bindings.put("local_opinion", drawWithWindow("local_opinion", poolWindows, WINDOW_L2, () -> topicPool.randomLocalOpinion(reactionBracket, random)));
        bindings.put("personal_reaction", drawWithWindow("personal_reaction", poolWindows, WINDOW_L2, () -> topicPool.randomPersonalReaction(reactionBracket, random)));
        bindings.put("danger_assessment", drawWithWindow("danger_assessment", poolWindows, WINDOW_L2, () -> topicPool.randomDangerAssessment(random)));

        // Category-specific pool bindings (pre-resolve so nested {subject_focus} etc. are substituted)
        // Drop-in window size for rumor_source, rumor_detail, perspective_detail, smalltalk_opener
        if (focus.getCategory() == TopicCategory.RUMORS) {
            bindings.put("rumor_detail", DialogueResolver.resolve(
                drawWithWindow("rumor_detail", poolWindows, WINDOW_DROPIN, () -> topicPool.randomRumorDetail(random)), bindings));
            bindings.put("rumor_source", DialogueResolver.resolve(
                drawWithWindow("rumor_source", poolWindows, WINDOW_DROPIN, () -> topicPool.randomRumorSource(random)), bindings));
        }
        bindings.put("perspective_detail", DialogueResolver.resolve(
            drawWithWindow("perspective_detail", poolWindows, WINDOW_DROPIN, () -> topicPool.randomPerspectiveDetail(random)), bindings));
        if (focus.getCategory() == TopicCategory.SMALLTALK) {
            bindings.put("smalltalk_opener", DialogueResolver.resolve(
                drawWithWindow("smalltalk_opener", poolWindows, WINDOW_DROPIN, () -> topicPool.randomSmalltalkOpener(random)), bindings));
        }

        // Quest bindings for quest bearers: use actual quest instance bindings
        if (isQuestBearer && focus.hasQuest() && focus.getQuestBindings() != null) {
            Map<String, String> qb = focus.getQuestBindings();

            // Copy quest narrative bindings directly (these come from the real quest)
            for (String key : List.of(
                    "quest_threat", "quest_threat_is", "quest_threat_has", "quest_threat_was",
                    "quest_threat_the", "quest_threat_The",
                    "quest_stakes", "quest_stakes_is", "quest_stakes_has", "quest_stakes_was",
                    "quest_stakes_the", "quest_stakes_The",
                    "quest_focus", "quest_focus_is", "quest_focus_has", "quest_focus_was",
                    "quest_focus_the", "quest_focus_The",
                    "quest_objective_summary",
                    "enemy_type", "enemy_type_plural", "quest_item")) {
                if (qb.containsKey(key)) bindings.put(key, qb.get(key));
            }

            // Still draw exposition/detail from pools but resolve against real quest bindings
            bindings.put("quest_exposition", DialogueResolver.resolve(topicPool.randomRumorDetail(random), bindings));
            bindings.put("quest_detail", DialogueResolver.resolve(topicPool.randomPerspectiveDetail(random), bindings));

            String tone = questPool.getToneForSituation(focus.getQuestSituationId());
            bindings.put("quest_accept_response",
                questPool.randomCounterAccept(focus.getQuestSituationId(), tone, random));
        }

        return bindings;
    }

    /**
     * Draw a subject matching targetCategory, with collision checking against already-used values.
     * Tries up to 3 times to find an unused subject, then falls back to any matching subject.
     */
    private TopicPoolRegistry.SubjectEntry drawUniqueSubject(String targetCategory, Set<String> usedValues, Random random) {
        for (int attempt = 0; attempt < 3; attempt++) {
            TopicPoolRegistry.SubjectEntry entry = topicPool.randomSubjectForCategoryExcluding(targetCategory, usedValues, random);
            if (!usedValues.contains(entry.value())) {
                return entry;
            }
        }
        // After 3 failures, accept any matching subject (even if duplicate)
        return topicPool.randomSubjectForCategory(targetCategory, random);
    }

    /**
     * Draw a pool value with rolling window dedup: if the drawn value was used within the last
     * windowSize draws of this pool, redraw up to 2 times before accepting.
     */
    private static String drawWithWindow(String poolName, Map<String, LinkedList<String>> windows,
                                          int windowSize, java.util.function.Supplier<String> drawer) {
        LinkedList<String> window = windows.computeIfAbsent(poolName, k -> new LinkedList<>());
        String value = drawer.get();
        int attempts = 0;
        while (window.contains(value) && attempts < 2) {
            value = drawer.get();
            attempts++;
        }
        window.add(value);
        if (window.size() > windowSize) window.removeFirst();
        return value;
    }

    /**
     * Sanitize a subject value into a safe ID component: lowercase alphanumeric with underscores.
     */
    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static boolean isSocialRole(String role) {
        return SOCIAL_ROLES.contains(role);
    }

    private static double openerChanceForBracket(String bracket) {
        return switch (bracket) {
            case "hostile" -> OPENER_CHANCE_HOSTILE;
            case "unfriendly" -> OPENER_CHANCE_UNFRIENDLY;
            case "neutral" -> OPENER_CHANCE_NEUTRAL;
            case "friendly" -> OPENER_CHANCE_FRIENDLY;
            case "loyal" -> OPENER_CHANCE_LOYAL;
            default -> OPENER_CHANCE_NEUTRAL;
        };
    }

    private static String dispositionBracket(int disposition) {
        return DispositionBracket.textPoolFromDisposition(disposition);
    }

    private static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
