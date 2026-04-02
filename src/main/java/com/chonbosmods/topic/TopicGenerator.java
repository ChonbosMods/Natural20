package com.chonbosmods.topic;

import com.chonbosmods.dialogue.DispositionBracket;
import com.chonbosmods.dialogue.ValenceType;
import com.chonbosmods.dialogue.model.DialogueGraph;
import com.chonbosmods.quest.DialogueResolver;
import com.chonbosmods.quest.QuestGenerator;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestPoolRegistry;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.stats.Skill;
import com.google.common.flogger.FluentLogger;

import java.util.*;

/**
 * Settlement-level orchestrator that generates all topics for every NPC in a settlement.
 * Seeds deterministically from the settlement's cell key so the same topics regenerate
 * on server restart.
 */
public class TopicGenerator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    // Role-based topic budgets: v2 uses lower budgets with richer per-topic content
    private static final int SOCIAL_MIN_TOPICS = 2;
    private static final int SOCIAL_MAX_TOPICS = 4;
    private static final int FUNCTIONAL_MIN_TOPICS = 0;
    private static final int FUNCTIONAL_MAX_TOPICS = 2;
    private static final Set<String> SOCIAL_ROLES = Set.of(
        "TavernKeeper", "ArtisanAlchemist", "ArtisanBlacksmith", "ArtisanCook", "Traveler"
    );

    private static final double RUMOR_RATIO = 0.4;
    private static final double QUEST_CHANCE_PER_SUBJECT = 0.40;

    // Stratified category decks for subject drawing
    private static final List<String> RUMOR_DECK = List.of(
        "danger", "sighting", "treasure", "corruption", "conflict", "disappearance", "migration", "omen"
    );
    private static final List<String> SMALLTALK_DECK = List.of(
        "trade", "weather", "craftsmanship", "community", "nature", "nostalgia", "curiosity", "festival"
    );

    private final TopicPoolRegistry topicPool;
    private final TopicTemplateRegistry templateRegistry;
    private final QuestPoolRegistry questPool;
    private final QuestGenerator questGenerator;
    private final PromptGroupRegistry promptGroups;

    public TopicGenerator(TopicPoolRegistry topicPool, TopicTemplateRegistry templateRegistry,
                          QuestPoolRegistry questPool, QuestGenerator questGenerator,
                          PromptGroupRegistry promptGroups) {
        this.topicPool = topicPool;
        this.templateRegistry = templateRegistry;
        this.questPool = questPool;
        this.questGenerator = questGenerator;
        this.promptGroups = promptGroups;
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
                    entry.questEligible(), entry.concrete(), entry.categories(),
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
                    entry.questEligible(), entry.concrete(), entry.categories(),
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
                    eligible.proper(), eligible.questEligible(), eligible.concrete(),
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
        PercentageDedup dedup = new PercentageDedup();
        Set<String> usedLabels = new HashSet<>();

        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = new ArrayList<>();
            for (SubjectFocus focus : npcSubjects.get(npcName)) {
                TopicGraphBuilder.TopicAssignment assignment = buildAssignment(focus, npc, random, dedup);
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
        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = npcAssignments.get(npcName);

            String greeting = topicPool.randomGreeting(random);
            String returnGreeting = topicPool.randomReturnGreeting(random);

            TopicGraphBuilder builder = new TopicGraphBuilder(
                npcName, 50, greeting, returnGreeting, assignments, topicPool, random, promptGroups
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
                    npcEntry.getKey(), focus.getSubjectValue(), focus.getCategories(), questTag);
            }
        }

        return results;
    }

    /**
     * Build a TopicAssignment using the coherent-entry pipeline.
     * Draws one PoolEntry per topic via PercentageDedup, picks a skill from the template,
     * and builds simplified bindings without L0/L1/L2 fragment draws.
     */
    private TopicGraphBuilder.TopicAssignment buildAssignment(
            SubjectFocus focus, NpcRecord npc, Random random,
            PercentageDedup dedup) {

        TopicTemplate template = templateRegistry.randomTemplateForSubject(
            focus.getCategories(), focus.isConcrete(), random);

        boolean isQuestBearer = focus.hasQuest();

        // Draw coherent entry from template's pool
        List<PoolEntry> pool = topicPool.getCoherentPool(template.id());
        PoolEntry entry;
        if (!pool.isEmpty()) {
            int idx = dedup.draw(template.id(), pool.size(), random);
            entry = pool.get(idx);
        } else {
            entry = new PoolEntry(0, "I have heard something about {subject_focus}...",
                List.of("That is all I know, really."), List.of("Make of it what you will."), null, ValenceType.NEUTRAL);
        }

        // Pick skill from template's skills array
        Skill skill = null;
        if (template.skills() != null && !template.skills().isEmpty()) {
            String skillName = template.skills().get(random.nextInt(template.skills().size()));
            try { skill = Skill.valueOf(skillName); }
            catch (IllegalArgumentException ignored) {}
        }

        Map<String, String> bindings = buildBindings(
            focus, npc.getGeneratedName(), npc.getDisposition(),
            isQuestBearer, template, entry, dedup, random);

        return new TopicGraphBuilder.TopicAssignment(
            focus.getSubjectId(), template.labelPattern(),
            bindings, true, isQuestBearer,
            skill, template, entry
        );
    }

    /**
     * Build simplified v2 bindings: subject focus, NPC name, drop-ins, tone framing,
     * entry content, and quest bindings. No L0/L1/L2 fragment draws.
     */
    private Map<String, String> buildBindings(SubjectFocus focus, String npcName, int disposition,
                                                 boolean isQuestBearer, TopicTemplate template,
                                                 PoolEntry entry, PercentageDedup dedup, Random random) {
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

        bindings.put("npc_name", npcName);

        // Drop-ins via percentage dedup
        bindings.put("time_ref", dedup.drawFrom("time_refs", topicPool.getTimeRefs(), random));
        bindings.put("direction", dedup.drawFrom("directions", topicPool.getDirections(), random));

        // Tone framing
        String bracket = dispositionBracket(disposition);
        FramingShape shape = isQuestBearer ? FramingShape.BARE : FramingShape.roll(bracket, random);
        bindings.put("tone_opener", shape.hasOpener()
            ? dedup.drawFrom("tone_opener_" + bracket, topicPool.getToneOpeners(bracket), random) + " "
            : "");
        bindings.put("tone_closer", shape.hasCloser()
            ? " " + dedup.drawFrom("tone_closer_" + bracket, topicPool.getToneClosers(bracket), random)
            : "");

        // Entry content: pre-resolve variables in pool text so nested tokens
        // like {subject_focus_the} inside entry.intro() get substituted.
        bindings.put("entry_intro", DialogueResolver.resolve(entry.intro(), bindings));
        if (!entry.reactions().isEmpty()) {
            bindings.put("entry_reaction", DialogueResolver.resolve(entry.reactions().getFirst(), bindings));
        }

        // Quest bindings for quest bearers
        if (isQuestBearer && focus.hasQuest() && focus.getQuestBindings() != null) {
            Map<String, String> qb = focus.getQuestBindings();
            for (String key : List.of(
                    "quest_threat", "quest_threat_is", "quest_threat_has", "quest_threat_was",
                    "quest_threat_the", "quest_threat_The",
                    "quest_stakes", "quest_stakes_is", "quest_stakes_has", "quest_stakes_was",
                    "quest_stakes_the", "quest_stakes_The",
                    "quest_focus", "quest_focus_is", "quest_focus_has", "quest_focus_was",
                    "quest_focus_the", "quest_focus_The",
                    "quest_objective_summary",
                    "quest_plot_step", "quest_outro",
                    "enemy_type", "enemy_type_plural", "quest_item")) {
                if (qb.containsKey(key)) bindings.put(key, qb.get(key));
            }

            String rawExposition;
            if ("peaceful".equals(qb.get("fetch_variant"))) {
                rawExposition = questPool.randomPeacefulFetchExposition(random);
            } else if (qb.containsKey("gather_category")) {
                rawExposition = questPool.randomCollectExposition(qb.get("gather_category"), random);
            } else {
                String situationId = focus.getQuestSituationId();
                rawExposition = questPool.randomExpositionForSituation(situationId, random);
                if (rawExposition == null || rawExposition.isEmpty()) {
                    rawExposition = "Something has gone wrong and the settlement needs help";
                }
            }
            bindings.put("quest_exposition", DialogueResolver.resolve(rawExposition, bindings));
            bindings.put("quest_detail", DialogueResolver.resolve(
                topicPool.randomPerspectiveDetail(random), bindings));

            // Resolve plotStep and outro through variable substitution
            if (bindings.containsKey("quest_plot_step")) {
                bindings.put("quest_plot_step", DialogueResolver.resolve(bindings.get("quest_plot_step"), bindings));
            }
            if (bindings.containsKey("quest_outro")) {
                bindings.put("quest_outro", DialogueResolver.resolve(bindings.get("quest_outro"), bindings));
            }

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
     * Sanitize a subject value into a safe ID component: lowercase alphanumeric with underscores.
     */
    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static boolean isSocialRole(String role) {
        return SOCIAL_ROLES.contains(role);
    }

    private static String dispositionBracket(int disposition) {
        return DispositionBracket.textPoolFromDisposition(disposition);
    }

    private static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
