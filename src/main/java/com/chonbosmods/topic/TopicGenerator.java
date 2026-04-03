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

    // Quest tuning (not shared with dry run)
    private static final double QUEST_CHANCE_PER_SUBJECT = 0.40;
    private static final double MIN_QUEST_RATIO = 0.25;
    private static final double MAX_QUEST_RATIO = 0.50;
    private static final int MIN_QUEST_FLOOR = 1;
    private static final int MAX_QUEST_FLOOR = 2;
    private static final int MAX_SUBJECT_DRAW_ATTEMPTS = 3;

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
    public Map<String, DialogueGraph> generate(SettlementRecord settlement, List<String> nearbySettlementNames) {
        List<NpcRecord> npcs = settlement.getNpcs();
        if (npcs.isEmpty()) {
            LOGGER.atWarning().log("Settlement %s has no NPCs, skipping topic generation", settlement.getCellKey());
            return Map.of();
        }

        Random random = new Random(settlement.getCellKey().hashCode());

        // Build settlement context for template variable resolution
        List<SettlementContext.NpcRef> npcRefs = new ArrayList<>();
        for (NpcRecord npc : npcs) {
            npcRefs.add(new SettlementContext.NpcRef(npc.getGeneratedName(), npc.getRole()));
        }
        SettlementContext ctx = new SettlementContext(
            settlement.deriveName(),
            npcRefs,
            settlement.getSettlementType().getPoiTypes(),
            settlement.getSettlementType().getMobTypes(),
            nearbySettlementNames
        );

        int npcCount = npcs.size();

        // Step 1: Roll topic budgets per NPC (role-based ranges)
        Map<String, Integer> topicBudgets = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            int min, max;
            if (isSocialRole(npc.getRole())) {
                min = TopicConstants.SOCIAL_MIN_TOPICS;
                max = TopicConstants.SOCIAL_MAX_TOPICS;
            } else {
                min = TopicConstants.FUNCTIONAL_MIN_TOPICS;
                max = TopicConstants.FUNCTIONAL_MAX_TOPICS;
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
            int rumorCount = (int) Math.ceil(budget * TopicConstants.RUMOR_RATIO);
            int smallTalkCount = budget - rumorCount;

            List<String> rumorDeck = new ArrayList<>(TopicConstants.RUMOR_DECK);
            List<String> smalltalkDeck = new ArrayList<>(TopicConstants.SMALLTALK_DECK);
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

        int minQuests = Math.max(MIN_QUEST_FLOOR, (int) Math.floor(npcCount * MIN_QUEST_RATIO));
        int maxQuests = Math.max(MAX_QUEST_FLOOR, (int) Math.floor(npcCount * MAX_QUEST_RATIO));

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

        // Step 4: Build assignments per NPC. No settlement-wide label dedup:
        // generated topics use template-derived labels (e.g. "Around Town", "Trade")
        // and different NPCs can independently have topics with the same label
        // since they'll draw different pool entries.
        Map<String, List<TopicGraphBuilder.TopicAssignment>> npcAssignments = new LinkedHashMap<>();
        PercentageDedup dedup = new PercentageDedup();

        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = new ArrayList<>();
            for (SubjectFocus focus : npcSubjects.get(npcName)) {
                assignments.add(buildAssignment(focus, npc, random, dedup, ctx));
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
                npcName, npc.getDisposition(), greeting, returnGreeting, assignments, topicPool, random
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
            PercentageDedup dedup, SettlementContext ctx) {

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
            entry = new PoolEntry(0, "I had something on my mind, but it's slipped away.",
                List.of("Happens more often than I'd like to admit."), List.of("Getting old, I suppose."), null, ValenceType.NEUTRAL);
        }

        // Pick skill from template's skills array
        Skill skill = null;
        if (template.skills() != null && !template.skills().isEmpty()) {
            String skillName = template.skills().get(random.nextInt(template.skills().size()));
            try { skill = Skill.valueOf(skillName); }
            catch (IllegalArgumentException ignored) {}
        }

        Map<String, String> bindings = buildBindings(
            focus, npc.getGeneratedName(), npc.getRole(), npc.getDisposition(),
            isQuestBearer, template, entry, dedup, random, ctx);

        // Quest bearers keep the template's subject-derived label pattern.
        // Generated smalltalk uses a static template-derived label.
        String labelPattern = isQuestBearer
            ? template.labelPattern()
            : TopicConstants.TEMPLATE_LABELS.getOrDefault(template.id(), template.id());

        return new TopicGraphBuilder.TopicAssignment(
            focus.getSubjectId(), labelPattern,
            bindings, true, isQuestBearer,
            skill, template, entry
        );
    }

    /**
     * Build bindings for template resolution. Generated smalltalk entries are self-contained
     * (no subject_focus variables). Quest bearers still get subject focus bindings for
     * quest exposition text.
     */
    private Map<String, String> buildBindings(SubjectFocus focus, String npcName, String npcRole,
                                                 int disposition, boolean isQuestBearer,
                                                 TopicTemplate template, PoolEntry entry,
                                                 PercentageDedup dedup, Random random,
                                                 SettlementContext ctx) {
        Map<String, String> bindings = new HashMap<>();

        // Subject focus bindings: only for quest bearers (quest exposition text uses them).
        // Generated smalltalk pool entries are self-contained and don't reference {subject_focus}.
        if (isQuestBearer) {
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
        }

        // Phase 0: settlement context variables
        bindings.put("settlement_name", ctx.settlementName());

        // NPC name references: {npc_name} = another NPC (not speaker), {npc_name_2} = a second other NPC
        List<SettlementContext.NpcRef> otherNpcs = ctx.npcs().stream()
            .filter(n -> !n.name().equals(npcName))
            .toList();
        if (otherNpcs.size() >= 2) {
            SettlementContext.NpcRef firstOther = otherNpcs.get(random.nextInt(otherNpcs.size()));
            bindings.put("npc_name", firstOther.name());
            List<SettlementContext.NpcRef> secondCandidates = otherNpcs.stream()
                .filter(n -> !n.name().equals(firstOther.name()))
                .toList();
            if (!secondCandidates.isEmpty()) {
                bindings.put("npc_name_2", secondCandidates.get(random.nextInt(secondCandidates.size())).name());
            }
        } else if (otherNpcs.size() == 1) {
            bindings.put("npc_name", otherNpcs.getFirst().name());
            // Only one other NPC: reuse for npc_name_2 (same person can be mentioned twice)
            bindings.put("npc_name_2", otherNpcs.getFirst().name());
        }
        // No else: when no other NPCs exist (CART), leave {npc_name} unbound.
        // Pool entries using {npc_name} won't be authored for CART settlements.

        // POI type
        if (!ctx.poiTypes().isEmpty()) {
            bindings.put("poi_type", ctx.poiTypes().get(random.nextInt(ctx.poiTypes().size())));
        }

        // Mob type
        if (!ctx.mobTypes().isEmpty()) {
            bindings.put("mob_type", ctx.mobTypes().get(random.nextInt(ctx.mobTypes().size())));
        }

        // Other settlement
        if (!ctx.nearbySettlementNames().isEmpty()) {
            bindings.put("other_settlement",
                ctx.nearbySettlementNames().get(random.nextInt(ctx.nearbySettlementNames().size())));
        }

        // Tier 1: role variables
        bindings.put("self_role", roleDisplayName(npcRole));
        String referencedNpcName = bindings.get("npc_name");
        if (referencedNpcName != null && !referencedNpcName.equals(npcName)) {
            for (SettlementContext.NpcRef ref : ctx.npcs()) {
                if (ref.name().equals(referencedNpcName)) {
                    bindings.put("npc_role", roleDisplayName(ref.role()));
                    break;
                }
            }
        }

        // Drop-ins (existing)
        bindings.put("time_ref", dedup.drawFrom("time_refs", topicPool.getTimeRefs(), random));
        bindings.put("direction", dedup.drawFrom("directions", topicPool.getDirections(), random));

        // Tier 3: flavor pool variables
        bindings.put("food_type", dedup.drawFrom("food_types", topicPool.getFoodTypes(), random));
        bindings.put("crop_type", dedup.drawFrom("crop_types", topicPool.getCropTypes(), random));
        bindings.put("wildlife_type", dedup.drawFrom("wildlife_types", topicPool.getWildlifeTypes(), random));
        String poiKey = bindings.getOrDefault("poi_type", "general");
        bindings.put("resource_type", dedup.drawFrom(
            "resource_types_" + poiKey,
            topicPool.getResourceTypes(poiKey),
            random));

        // Tone framing (valence-aware)
        String bracket = dispositionBracket(disposition);
        FramingShape shape = isQuestBearer ? FramingShape.BARE : FramingShape.roll(bracket, random);
        ValenceType entryValence = entry.valence();
        bindings.put("tone_opener", shape.hasOpener()
            ? topicPool.randomToneOpener(bracket, entryValence, random) + " "
            : "");
        bindings.put("tone_closer", shape.hasCloser()
            ? " " + topicPool.randomToneCloser(bracket, entryValence, random)
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
        for (int attempt = 0; attempt < MAX_SUBJECT_DRAW_ATTEMPTS; attempt++) {
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
        return TopicConstants.SOCIAL_ROLES.contains(role);
    }

    private static String dispositionBracket(int disposition) {
        return DispositionBracket.textPoolFromDisposition(disposition);
    }

    private static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Map internal role name to lowercase natural-language display string.
     */
    public static String roleDisplayName(String role) {
        return switch (role) {
            case "ArtisanBlacksmith" -> "blacksmith";
            case "ArtisanAlchemist" -> "alchemist";
            case "ArtisanCook" -> "cook";
            case "TavernKeeper" -> "tavern keeper";
            default -> role.toLowerCase();
        };
    }
}
