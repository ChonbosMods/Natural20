package com.chonbosmods.topic;

import com.chonbosmods.dialogue.model.DialogueGraph;
import com.chonbosmods.quest.DialogueResolver;
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

    private static final int MIN_TOPICS_PER_NPC = 2;
    private static final int MAX_TOPICS_PER_NPC = 4;
    private static final double RUMOR_RATIO = 0.6;
    private static final double QUEST_CHANCE_PER_SUBJECT = 0.25;
    private static final int MIN_QUESTS_PER_SETTLEMENT = 2;
    private static final int MAX_QUESTS_PER_SETTLEMENT = 8;
    private static final int MIN_NPCS_PER_SUBJECT = 3;
    private static final double EXTRA_NPC_CHANCE = 0.30;
    private static final double VISIBILITY_CHANCE = 0.40;

    private final TopicPoolRegistry topicPool;
    private final TopicTemplateRegistry templateRegistry;
    private final QuestPoolRegistry questPool;

    public TopicGenerator(TopicPoolRegistry topicPool, TopicTemplateRegistry templateRegistry,
                          QuestPoolRegistry questPool) {
        this.topicPool = topicPool;
        this.templateRegistry = templateRegistry;
        this.questPool = questPool;
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

        // Step 1: Roll topic budgets per NPC
        Map<String, Integer> topicBudgets = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            int budget = MIN_TOPICS_PER_NPC + random.nextInt(MAX_TOPICS_PER_NPC - MIN_TOPICS_PER_NPC + 1);
            topicBudgets.put(npc.getGeneratedName(), budget);
        }

        // Step 2: Roll shared subjects
        int subjectCount = (int) Math.ceil(npcCount * 0.6);
        int rumorCount = (int) Math.ceil(subjectCount * RUMOR_RATIO);
        int smallTalkCount = subjectCount - rumorCount;

        List<SubjectFocus> subjects = new ArrayList<>();
        for (int i = 0; i < subjectCount; i++) {
            TopicCategory category = i < rumorCount ? TopicCategory.RUMORS : TopicCategory.SMALLTALK;
            TopicPoolRegistry.SubjectEntry entry = topicPool.randomSubject(random);
            String subjectId = "subj_" + i + "_" + sanitize(entry.value());
            subjects.add(new SubjectFocus(subjectId, entry.value(), entry.plural(), entry.proper(), entry.questEligible(), category));
        }

        // Step 3: Roll quest placement (25% per subject, min 2, max 8)
        List<Integer> questCandidates = new ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            if (random.nextDouble() < QUEST_CHANCE_PER_SUBJECT) {
                questCandidates.add(i);
            }
        }

        // Enforce minimum quests: add random subjects until we have enough
        while (questCandidates.size() < MIN_QUESTS_PER_SETTLEMENT && questCandidates.size() < subjects.size()) {
            int idx = random.nextInt(subjects.size());
            if (!questCandidates.contains(idx)) {
                questCandidates.add(idx);
            }
        }

        // Enforce maximum quests
        while (questCandidates.size() > MAX_QUESTS_PER_SETTLEMENT) {
            questCandidates.remove(random.nextInt(questCandidates.size()));
        }

        // Quest subjects must use quest-eligible values: swap out non-eligible entries
        for (int qi : questCandidates) {
            SubjectFocus focus = subjects.get(qi);
            if (!focus.isQuestEligible()) {
                TopicPoolRegistry.SubjectEntry eligible = topicPool.randomQuestEligibleSubject(random);
                String newId = "subj_" + qi + "_" + sanitize(eligible.value());
                subjects.set(qi, new SubjectFocus(newId, eligible.value(), eligible.plural(),
                    eligible.proper(), eligible.questEligible(), focus.getCategory()));
            }
        }

        // Mark quest bearers: assign to a random NPC
        List<String> npcNames = npcs.stream().map(NpcRecord::getGeneratedName).toList();
        Map<String, NpcRecord> npcByName = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) npcByName.put(npc.getGeneratedName(), npc);

        for (int qi : questCandidates) {
            SubjectFocus focus = subjects.get(qi);
            String bearer = npcNames.get(random.nextInt(npcNames.size()));
            focus.setQuestBearer(bearer, focus.getSubjectId());
            NpcRecord rec = npcByName.get(bearer);
            LOGGER.atInfo().log("  QUEST BEARER: %s (%s) holds quest for '%s' | /tp %d %d %d",
                bearer, rec != null ? rec.getRole() : "?",
                focus.getSubjectValue(),
                rec != null ? (int) rec.getSpawnX() : 0,
                rec != null ? (int) rec.getSpawnY() : 0,
                rec != null ? (int) rec.getSpawnZ() : 0);
        }

        // Step 4: Distribute subjects across NPCs
        for (SubjectFocus focus : subjects) {
            distributeSubject(focus, npcNames, npcCount, random);
        }

        // Step 5: Roll visibility per subject/NPC
        for (SubjectFocus focus : subjects) {
            rollVisibility(focus, random);
        }

        // Step 6 & 7: Generate perspectives and build graphs per NPC
        Map<String, List<TopicGraphBuilder.TopicAssignment>> npcAssignments = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            npcAssignments.put(npc.getGeneratedName(), new ArrayList<>());
        }

        for (SubjectFocus focus : subjects) {
            for (String npcName : focus.getAssignedNpcs()) {
                TopicGraphBuilder.TopicAssignment assignment = buildAssignment(focus, npcName, random);
                npcAssignments.get(npcName).add(assignment);
            }
        }

        // Step 8: Ensure minimum topics per NPC
        ensureMinimumTopics(npcAssignments, subjects, npcNames, random);

        // Build final graphs
        Map<String, DialogueGraph> results = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = npcAssignments.get(npcName);

            String greeting = topicPool.randomGreeting(random);
            String returnGreeting = topicPool.randomReturnGreeting(random);

            TopicGraphBuilder builder = new TopicGraphBuilder(
                npcName, 50, greeting, returnGreeting, assignments
            );
            DialogueGraph graph = builder.build();

            if (graph.validate()) {
                results.put(npcName, graph);
            } else {
                LOGGER.atWarning().log("Generated invalid dialogue graph for NPC %s in settlement %s",
                    npcName, settlement.getCellKey());
            }
        }

        LOGGER.atInfo().log("Generated %d dialogue graphs for settlement %s (%d subjects, %d quests)",
            results.size(), settlement.getCellKey(), subjects.size(), questCandidates.size());

        // Debug: log per-subject visibility and quest assignments
        for (SubjectFocus focus : subjects) {
            StringBuilder sb = new StringBuilder();
            sb.append("  Subject '%s' (%s)".formatted(focus.getSubjectValue(), focus.getCategory()));
            if (focus.hasQuest()) {
                sb.append(" [QUEST bearer=%s]".formatted(focus.getQuestBearingNpc()));
            }
            sb.append(": ");
            List<String> npcEntries = new ArrayList<>();
            for (var entry : focus.getNpcVisibility().entrySet()) {
                npcEntries.add("%s=%s".formatted(entry.getKey(), entry.getValue() ? "VISIBLE" : "HIDDEN"));
            }
            sb.append(String.join(", ", npcEntries));
            LOGGER.atInfo().log(sb.toString());
        }

        return results;
    }

    /**
     * Distribute a subject across NPCs: min 3 NPCs (or npcCount if smaller),
     * then 30% diminishing chance for additional NPCs.
     */
    private void distributeSubject(SubjectFocus focus, List<String> npcNames, int npcCount, Random random) {
        int minAssign = Math.min(MIN_NPCS_PER_SUBJECT, npcCount);

        // Shuffle a copy to randomize which NPCs get assigned
        List<String> shuffled = new ArrayList<>(npcNames);
        Collections.shuffle(shuffled, random);

        // If this subject has a quest bearer, ensure they're in the first minAssign
        String bearer = focus.getQuestBearingNpc();
        if (bearer != null) {
            shuffled.remove(bearer);
            shuffled.addFirst(bearer);
        }

        // Assign minimum NPCs
        int assigned = 0;
        for (int i = 0; i < minAssign && i < shuffled.size(); i++) {
            focus.assignNpc(shuffled.get(i), false);
            assigned++;
        }

        // Extra NPCs with 30% diminishing chance
        for (int i = assigned; i < shuffled.size(); i++) {
            if (random.nextDouble() < EXTRA_NPC_CHANCE) {
                focus.assignNpc(shuffled.get(i), false);
            } else {
                break; // Diminishing: stop on first failure
            }
        }
    }

    /**
     * Roll visibility: 1 NPC guaranteed visible (startLearned:true), others get 40% chance.
     */
    private void rollVisibility(SubjectFocus focus, Random random) {
        Set<String> assignedNpcs = focus.getAssignedNpcs();
        if (assignedNpcs.isEmpty()) return;

        // Pick one guaranteed visible NPC
        List<String> npcList = new ArrayList<>(assignedNpcs);
        String guaranteedVisible = npcList.get(random.nextInt(npcList.size()));

        // Re-assign all with visibility flags
        Map<String, Boolean> visibilityMap = new LinkedHashMap<>();
        for (String npc : npcList) {
            if (npc.equals(guaranteedVisible)) {
                visibilityMap.put(npc, true);
            } else {
                visibilityMap.put(npc, random.nextDouble() < VISIBILITY_CHANCE);
            }
        }

        // Clear and re-add with correct visibility (SubjectFocus.assignNpc replaces)
        // Since assignNpc uses a LinkedHashMap, re-assigning overwrites the entry
        for (var entry : visibilityMap.entrySet()) {
            focus.assignNpc(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Build a TopicAssignment for a specific NPC on a specific subject.
     */
    private TopicGraphBuilder.TopicAssignment buildAssignment(SubjectFocus focus, String npcName, Random random) {
        TopicTemplate template = templateRegistry.randomTemplate(focus.getCategory(), random);
        boolean isQuestBearer = npcName.equals(focus.getQuestBearingNpc());

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
                "I've heard something about {subject_focus}...", List.of(), null
            );
        }

        // Build variable bindings
        Map<String, String> bindings = buildBindings(focus, npcName, isQuestBearer, template.id(), random);

        return new TopicGraphBuilder.TopicAssignment(
            focus.getSubjectId(),
            template.labelTemplate(),
            perspective,
            bindings,
            focus.isVisibleFor(npcName),
            isQuestBearer && focus.hasQuest()
        );
    }

    /**
     * Build variable bindings for a topic assignment.
     */
    private Map<String, String> buildBindings(SubjectFocus focus, String npcName,
                                               boolean isQuestBearer, String templateId, Random random) {
        Map<String, String> bindings = new HashMap<>();

        // Subject focus bindings
        bindings.put("subject_focus", focus.getSubjectValue());
        bindings.put("subject_focus_is", focus.isPlural() ? "are" : "is");
        bindings.put("subject_focus_has", focus.isPlural() ? "have" : "has");
        bindings.put("subject_focus_was", focus.isPlural() ? "were" : "was");
        bindings.put("subject_focus_the", focus.isProper() ? focus.getSubjectValue() : "the " + focus.getSubjectValue());
        bindings.put("subject_focus_The", focus.isProper() ? focus.getSubjectValue() : "The " + focus.getSubjectValue());

        // NPC name
        bindings.put("npc_name", npcName);

        // New drop-in pool bindings
        bindings.put("time_ref", topicPool.randomTimeRef(random));
        bindings.put("direction", topicPool.randomDirection(random));

        // Tone bindings (bracket-filtered by disposition)
        String bracket = dispositionBracket(50); // default disposition for generated topics
        bindings.put("tone_opener", topicPool.randomToneOpener(bracket, random));
        bindings.put("tone_closer", topicPool.randomToneCloser(bracket, random));

        // Fragment pool bindings: Layer 0
        bindings.put("creature_sighting", topicPool.randomCreatureSighting(random));
        bindings.put("strange_event", topicPool.randomStrangeEvent(random));
        bindings.put("trade_gossip", topicPool.randomTradeGossip(random));
        bindings.put("local_complaint", topicPool.randomLocalComplaint(random));
        bindings.put("traveler_news", topicPool.randomTravelerNews(random));

        // Fragment pool bindings: Layer 0 (new topic-matched)
        bindings.put("weather_observation", topicPool.randomWeatherObservation(random));
        bindings.put("craft_observation", topicPool.randomCraftObservation(random));
        bindings.put("community_observation", topicPool.randomCommunityObservation(random));
        bindings.put("nature_observation", topicPool.randomNatureObservation(random));
        bindings.put("nostalgia_observation", topicPool.randomNostalgiaObservation(random));
        bindings.put("curiosity_observation", topicPool.randomCuriosityObservation(random));
        bindings.put("festival_observation", topicPool.randomFestivalObservation(random));
        bindings.put("treasure_rumor", topicPool.randomTreasureRumor(random));
        bindings.put("conflict_rumor", topicPool.randomConflictRumor(random));

        // Fragment pool bindings: Layer 1
        bindings.put("creature_detail", topicPool.randomCreatureDetail(random));
        bindings.put("event_detail", topicPool.randomEventDetail(random));
        bindings.put("trade_detail", topicPool.randomTradeDetail(random));
        bindings.put("location_detail", topicPool.randomLocationDetail(random));

        // Fragment pool bindings: Layer 1 (new topic-matched)
        bindings.put("weather_detail", topicPool.randomWeatherDetail(random));
        bindings.put("craft_detail", topicPool.randomCraftDetail(random));
        bindings.put("community_detail", topicPool.randomCommunityDetail(random));
        bindings.put("nature_detail", topicPool.randomNatureDetail(random));
        bindings.put("nostalgia_detail", topicPool.randomNostalgiaDetail(random));
        bindings.put("curiosity_detail", topicPool.randomCuriosityDetail(random));
        bindings.put("festival_detail", topicPool.randomFestivalDetail(random));
        bindings.put("treasure_detail", topicPool.randomTreasureDetail(random));
        bindings.put("conflict_detail", topicPool.randomConflictDetail(random));

        // Fragment pool bindings: Layer 2
        // Reaction bracket: rumors + nature + curiosity = intense, all other smalltalk = mild
        String reactionBracket = (focus.getCategory() == TopicCategory.RUMORS
            || "smalltalk_nature".equals(templateId)
            || "smalltalk_curiosity".equals(templateId)) ? "intense" : "mild";
        bindings.put("local_opinion", topicPool.randomLocalOpinion(random));
        bindings.put("personal_reaction", topicPool.randomPersonalReaction(reactionBracket, random));
        bindings.put("danger_assessment", topicPool.randomDangerAssessment(random));

        // Category-specific pool bindings (pre-resolve so nested {subject_focus} etc. are substituted)
        if (focus.getCategory() == TopicCategory.RUMORS) {
            bindings.put("rumor_detail", DialogueResolver.resolve(topicPool.randomRumorDetail(random), bindings));
            bindings.put("rumor_source", DialogueResolver.resolve(topicPool.randomRumorSource(random), bindings));
        }
        bindings.put("perspective_detail", DialogueResolver.resolve(topicPool.randomPerspectiveDetail(random), bindings));
        if (focus.getCategory() == TopicCategory.SMALLTALK) {
            bindings.put("smalltalk_opener", DialogueResolver.resolve(topicPool.randomSmalltalkOpener(random), bindings));
        }

        // Quest bindings for quest bearers
        if (isQuestBearer && focus.hasQuest()) {
            QuestPoolRegistry.NarrativeEntry threat = questPool.randomThreat(random);
            bindings.put("quest_threat", threat.value());
            bindings.put("quest_threat_is", threat.plural() ? "are" : "is");
            bindings.put("quest_threat_has", threat.plural() ? "have" : "has");
            bindings.put("quest_threat_was", threat.plural() ? "were" : "was");
            bindings.put("quest_threat_the", threat.proper() ? threat.value() : "the " + threat.value());
            bindings.put("quest_threat_The", threat.proper() ? threat.value() : "The " + threat.value());

            QuestPoolRegistry.NarrativeEntry stakes = questPool.randomStakes(random);
            bindings.put("quest_stakes", stakes.value());
            bindings.put("quest_stakes_is", stakes.plural() ? "are" : "is");
            bindings.put("quest_stakes_has", stakes.plural() ? "have" : "has");
            bindings.put("quest_stakes_was", stakes.plural() ? "were" : "was");
            bindings.put("quest_stakes_detail", stakes.value());
            bindings.put("quest_stakes_the", stakes.proper() ? stakes.value() : "the " + stakes.value());
            bindings.put("quest_stakes_The", stakes.proper() ? stakes.value() : "The " + stakes.value());

            bindings.put("quest_exposition", DialogueResolver.resolve(topicPool.randomRumorDetail(random), bindings));
            bindings.put("quest_detail", DialogueResolver.resolve(topicPool.randomPerspectiveDetail(random), bindings));

            String tone = questPool.getToneForSituation(focus.getQuestSituationId());
            bindings.put("quest_accept_response",
                questPool.randomCounterAccept(focus.getQuestSituationId(), tone, random));
        }

        return bindings;
    }

    /**
     * Ensure every NPC has at least {@link #MIN_TOPICS_PER_NPC} topics.
     * If an NPC is short, assign them to existing subjects they don't already have.
     */
    private void ensureMinimumTopics(Map<String, List<TopicGraphBuilder.TopicAssignment>> npcAssignments,
                                      List<SubjectFocus> subjects, List<String> npcNames, Random random) {
        for (String npcName : npcNames) {
            List<TopicGraphBuilder.TopicAssignment> assignments = npcAssignments.get(npcName);
            if (assignments.size() >= MIN_TOPICS_PER_NPC) continue;

            // Find subjects this NPC doesn't already have
            Set<String> existingSubjects = new HashSet<>();
            for (TopicGraphBuilder.TopicAssignment a : assignments) {
                existingSubjects.add(a.subjectId());
            }

            List<SubjectFocus> available = new ArrayList<>();
            for (SubjectFocus focus : subjects) {
                if (!existingSubjects.contains(focus.getSubjectId())) {
                    available.add(focus);
                }
            }
            Collections.shuffle(available, random);

            for (SubjectFocus focus : available) {
                if (assignments.size() >= MIN_TOPICS_PER_NPC) break;

                focus.assignNpc(npcName, random.nextDouble() < VISIBILITY_CHANCE);
                TopicGraphBuilder.TopicAssignment assignment = buildAssignment(focus, npcName, random);
                assignments.add(assignment);
            }

            // If still short (very few subjects), create new subjects
            while (assignments.size() < MIN_TOPICS_PER_NPC) {
                TopicCategory category = random.nextBoolean() ? TopicCategory.RUMORS : TopicCategory.SMALLTALK;
                TopicPoolRegistry.SubjectEntry entry = topicPool.randomSubject(random);
                int idx = subjects.size();
                String subjectId = "subj_" + idx + "_" + sanitize(entry.value());
                SubjectFocus newFocus = new SubjectFocus(subjectId, entry.value(), entry.plural(), entry.proper(), entry.questEligible(), category);
                newFocus.assignNpc(npcName, true);
                subjects.add(newFocus);

                TopicGraphBuilder.TopicAssignment assignment = buildAssignment(newFocus, npcName, random);
                assignments.add(assignment);
            }
        }
    }

    /**
     * Sanitize a subject value into a safe ID component: lowercase alphanumeric with underscores.
     */
    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String dispositionBracket(int disposition) {
        if (disposition < 45) return "hostile";
        if (disposition < 60) return "neutral";
        return "friendly";
    }
}
