package com.chonbosmods.topic;

import com.chonbosmods.dialogue.DispositionBracket;
import com.chonbosmods.dialogue.ValenceType;
import com.chonbosmods.dialogue.model.DialogueGraph;
import com.chonbosmods.quest.DialogueResolver;
import com.chonbosmods.quest.QuestGenerator;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.ui.EntityHighlight;
import com.google.common.flogger.FluentLogger;

import java.util.*;

/**
 * Settlement-level orchestrator that generates all topics for every NPC in a settlement.
 * Seeds deterministically from the settlement's cell key mixed with the world's
 * stable UUID so each world produces distinct dialogue for the same cell.
 *
 * <p>Maintains a per-world {@link PercentageDedup} shared across settlements so
 * that template entries, greetings and tone openers/closers don't repeat across
 * the world until ~80% of each pool has been exhausted. Restart-determinism is
 * provided by iterating settlements in {@code placedAt} order during bulk
 * regeneration (see {@code Natural20.startup}); no on-disk persistence is
 * required since the dedup is a pure function of seed + iteration order.
 */
public class TopicGenerator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    // Quest tuning (not shared with dry run). Quest bearer selection is now
    // per-NPC (not per-subject) so the smalltalk budget can be capped to leave
    // room for the runtime-injected quest topic.
    private static final double QUEST_CHANCE_PER_NPC = 0.40;
    private static final double MIN_QUEST_RATIO = 0.25;
    private static final double MAX_QUEST_RATIO = 0.50;
    private static final int MIN_QUEST_FLOOR = 1;
    private static final int MAX_QUEST_FLOOR = 2;
    private final TopicPoolRegistry topicPool;
    private final TopicTemplateRegistry templateRegistry;
    private final QuestGenerator questGenerator;

    /** Shared dedup state, one per world. World-gen is single-threaded per world. */
    private final Map<UUID, PercentageDedup> dedupByWorld = new HashMap<>();

    public TopicGenerator(TopicPoolRegistry topicPool, TopicTemplateRegistry templateRegistry,
                          QuestGenerator questGenerator) {
        this.topicPool = topicPool;
        this.templateRegistry = templateRegistry;
        this.questGenerator = questGenerator;
    }

    /**
     * Reset the in-memory dedup state for the given world. Called by the
     * bulk-regeneration path on startup before iterating settlements in
     * placedAt order, so the dedup evolves identically to its original
     * chronological order.
     */
    public void resetDedupForWorld(UUID worldUUID) {
        dedupByWorld.remove(worldUUID);
    }

    private PercentageDedup dedupFor(UUID worldUUID) {
        return dedupByWorld.computeIfAbsent(worldUUID, k -> new PercentageDedup());
    }

    /**
     * Mix the world's stable UUID into the per-cell seed so the same cell in
     * different worlds produces distinct RNG streams. Returns 0 for a null
     * UUID to preserve behavior in tests / dry runs that lack a world.
     */
    private static long worldEntropy(@javax.annotation.Nullable UUID worldUUID) {
        if (worldUUID == null) return 0L;
        return worldUUID.getMostSignificantBits() ^ worldUUID.getLeastSignificantBits();
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

        UUID worldUUID = settlement.getWorldUUID();
        long baseSeed = ((long) settlement.getCellKey().hashCode()) ^ worldEntropy(worldUUID);
        Random random = new Random(baseSeed);
        PercentageDedup dedup = dedupFor(worldUUID);

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

        // Step 1: Decide quest bearer NPCs up front. Done BEFORE topic budgets
        // so each bearer's smalltalk budget can be reduced by 1 to leave room
        // for the runtime-injected quest topic (total topics per NPC <= 3).
        int minQuests = Math.max(MIN_QUEST_FLOOR, (int) Math.floor(npcCount * MIN_QUEST_RATIO));
        int maxQuests = Math.max(MAX_QUEST_FLOOR, (int) Math.floor(npcCount * MAX_QUEST_RATIO));
        minQuests = Math.min(minQuests, npcCount);
        maxQuests = Math.min(maxQuests, npcCount);

        LinkedHashSet<String> questBearerNpcs = new LinkedHashSet<>();
        for (NpcRecord npc : npcs) {
            if (random.nextDouble() < QUEST_CHANCE_PER_NPC) {
                questBearerNpcs.add(npc.getGeneratedName());
            }
        }
        while (questBearerNpcs.size() < minQuests) {
            NpcRecord pick = npcs.get(random.nextInt(npcCount));
            questBearerNpcs.add(pick.getGeneratedName());
        }
        while (questBearerNpcs.size() > maxQuests) {
            String[] names = questBearerNpcs.toArray(new String[0]);
            questBearerNpcs.remove(names[random.nextInt(names.length)]);
        }

        // Step 2: Roll smalltalk topic budgets per NPC. Role-based ranges with
        // guard / functional / social tiers. Quest bearers get their budget
        // capped so total topics (smalltalk + quest) <= MAX_TOTAL_TOPICS_PER_NPC;
        // they also get a floor of 1 so the quest generator has a subject to
        // read affinities from.
        Map<String, Integer> topicBudgets = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            int min, max;
            String role = npc.getRole();
            if (TopicConstants.GUARD_ROLES.contains(role)) {
                min = TopicConstants.GUARD_MIN_TOPICS;
                max = TopicConstants.GUARD_MAX_TOPICS;
            } else if (isSocialRole(role)) {
                min = TopicConstants.SOCIAL_MIN_TOPICS;
                max = TopicConstants.SOCIAL_MAX_TOPICS;
            } else {
                min = TopicConstants.FUNCTIONAL_MIN_TOPICS;
                max = TopicConstants.FUNCTIONAL_MAX_TOPICS;
            }
            int budget = min + random.nextInt(max - min + 1);
            boolean isQuestBearer = questBearerNpcs.contains(npc.getGeneratedName());
            int maxSmalltalk = TopicConstants.MAX_TOTAL_TOPICS_PER_NPC - (isQuestBearer ? 1 : 0);
            if (budget > maxSmalltalk) budget = maxSmalltalk;
            if (isQuestBearer && budget < 1) budget = 1;
            topicBudgets.put(npc.getGeneratedName(), budget);
        }

        // Step 3: Each NPC gets 2-3 topic labels based on role, then draws
        // categories from those labels to fill their topic budget.
        List<SubjectFocus> allSubjects = new ArrayList<>();
        Map<String, List<SubjectFocus>> npcSubjects = new LinkedHashMap<>();
        Map<String, NpcRecord> npcByName = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) npcByName.put(npc.getGeneratedName(), npc);

        for (int npcIdx = 0; npcIdx < npcs.size(); npcIdx++) {
            NpcRecord npc = npcs.get(npcIdx);
            String npcName = npc.getGeneratedName();
            int budget = topicBudgets.get(npcName);

            // Per-NPC seeded random for deterministic label/category selection
            Random deckRandom = new Random(baseSeed ^ ((long) npcIdx * 31));

            // Select 2-3 labels based on role
            List<String> roleLabels = TopicConstants.ROLE_LABELS.getOrDefault(
                npc.getRole(), TopicConstants.DEFAULT_LABELS);
            int labelCount = Math.min(budget, Math.min(3, roleLabels.size()));
            List<String> selectedLabels = new ArrayList<>(roleLabels.subList(0, labelCount));

            List<SubjectFocus> npcTopics = new ArrayList<>();
            int subjectBase = allSubjects.size();

            for (int i = 0; i < budget; i++) {
                // Round-robin across selected labels
                String label = selectedLabels.get(i % selectedLabels.size());
                List<String> categories = TopicConstants.LABEL_CATEGORIES.get(label);
                String category = categories.get(deckRandom.nextInt(categories.size()));

                String subjectId = "topic_" + (subjectBase + i) + "_" + category;
                SubjectFocus focus = new SubjectFocus(subjectId, category, false, false,
                    false, false, List.of(category), "narrative_only", List.of());
                npcTopics.add(focus);
                allSubjects.add(focus);
            }

            npcSubjects.put(npcName, npcTopics);
        }

        // Step 4: Generate quests for pre-selected bearers. Each bearer's
        // first subject is used as the affinity hook; if that subject is not
        // quest-eligible, swap it for one that is. Quest bearers were chosen
        // in Step 1 and always have >=1 smalltalk subject due to the budget
        // floor in Step 2.
        for (String bearerName : questBearerNpcs) {
            List<SubjectFocus> ownerTopics = npcSubjects.get(bearerName);
            if (ownerTopics == null || ownerTopics.isEmpty()) {
                LOGGER.atWarning().log("Quest bearer %s has no subjects; skipping quest generation", bearerName);
                continue;
            }
            SubjectFocus focus = ownerTopics.get(0);
            if (!focus.isQuestEligible()) {
                TopicPoolRegistry.SubjectEntry eligible = topicPool.randomQuestEligibleSubject(random);
                String newId = "subj_" + sanitize(bearerName) + "_" + sanitize(eligible.value());
                SubjectFocus replacement = new SubjectFocus(newId, eligible.value(), eligible.plural(),
                    eligible.proper(), eligible.questEligible(), eligible.concrete(),
                    eligible.categories(), eligible.poiType(), eligible.questAffinities());
                ownerTopics.set(0, replacement);
                int allIdx = allSubjects.indexOf(focus);
                if (allIdx >= 0) allSubjects.set(allIdx, replacement);
                focus = replacement;
            }
            NpcRecord bearerRecord = npcByName.get(bearerName);
            QuestInstance preQuest = questGenerator.generate(
                bearerRecord.getRole(), bearerName,
                settlement.getCellKey(),
                bearerRecord.getSpawnX(), bearerRecord.getSpawnZ(),
                Set.of(),
                focus.getQuestAffinities(),
                focus.getPoiType(),
                focus.getSubjectValue()
            );

            if (preQuest != null) {
                bearerRecord.setPreGeneratedQuest(preQuest);
                LOGGER.atInfo().log("  QUEST BEARER: %s (%s) quest=%s | /tp %d %d %d",
                    bearerName, bearerRecord.getRole(),
                    preQuest.getSituationId(),
                    (int) bearerRecord.getSpawnX(),
                    (int) bearerRecord.getSpawnY(),
                    (int) bearerRecord.getSpawnZ());
            } else {
                LOGGER.atWarning().log("  QUEST BEARER: %s failed to generate quest, demoting to normal topic", bearerName);
            }
        }

        // Step 5: Build assignments per NPC. Dedup state is shared across all
        // settlements within the world (see dedupFor), so template entries,
        // greetings, and tone openers/closers stay varied beyond a single town.
        Map<String, List<TopicGraphBuilder.TopicAssignment>> npcAssignments = new LinkedHashMap<>();

        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = new ArrayList<>();
            for (SubjectFocus focus : npcSubjects.get(npcName)) {
                assignments.add(buildAssignment(focus, npc, random, dedup, ctx, npc.getPreGeneratedQuest()));
            }
            npcAssignments.put(npcName, assignments);
        }

        // Build final graphs
        Map<String, DialogueGraph> results = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = npcAssignments.get(npcName);

            String greeting = topicPool.randomGreeting(dedup, random);
            String returnGreeting = topicPool.randomReturnGreeting(dedup, random);

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
            results.size(), settlement.getCellKey(), allSubjects.size(), questBearerNpcs.size());

        // Debug: log per-NPC subject assignments
        for (var npcEntry : npcSubjects.entrySet()) {
            for (SubjectFocus focus : npcEntry.getValue()) {
                LOGGER.atFine().log("  %s: '%s' (%s)",
                    npcEntry.getKey(), focus.getSubjectValue(), focus.getCategories());
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
            PercentageDedup dedup, SettlementContext ctx,
            @javax.annotation.Nullable QuestInstance preGeneratedQuest) {

        TopicTemplate template = templateRegistry.randomTemplateForSubject(
            focus.getCategories(), focus.isConcrete(), random);

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
            template, entry, dedup, random, ctx, preGeneratedQuest);

        String labelPattern = TopicConstants.CATEGORY_LABEL.getOrDefault(template.id(), template.id());

        return new TopicGraphBuilder.TopicAssignment(
            focus.getSubjectId(), labelPattern,
            bindings, true,
            skill, template, entry
        );
    }

    /**
     * Build bindings for template resolution. Generated smalltalk entries are
     * self-contained (no subject_focus variables).
     */
    private Map<String, String> buildBindings(SubjectFocus focus, String npcName, String npcRole,
                                                 int disposition,
                                                 TopicTemplate template, PoolEntry entry,
                                                 PercentageDedup dedup, Random random,
                                                 SettlementContext ctx,
                                                 @javax.annotation.Nullable QuestInstance preGeneratedQuest) {
        Map<String, String> bindings = new HashMap<>();

        // Phase 0: settlement context variables (entity names wrapped for purple highlighting)
        bindings.put("settlement_name", EntityHighlight.wrap(ctx.settlementName()));

        // NPC name references: {npc_name} = another NPC (not speaker), {npc_name_2} = a second other NPC
        List<SettlementContext.NpcRef> otherNpcs = ctx.npcs().stream()
            .filter(n -> !n.name().equals(npcName))
            .toList();
        if (otherNpcs.size() >= 2) {
            SettlementContext.NpcRef firstOther = otherNpcs.get(random.nextInt(otherNpcs.size()));
            bindings.put("npc_name", EntityHighlight.wrap(firstOther.name()));
            List<SettlementContext.NpcRef> secondCandidates = otherNpcs.stream()
                .filter(n -> !n.name().equals(firstOther.name()))
                .toList();
            if (!secondCandidates.isEmpty()) {
                bindings.put("npc_name_2", EntityHighlight.wrap(
                    secondCandidates.get(random.nextInt(secondCandidates.size())).name()));
            }
        } else if (otherNpcs.size() == 1) {
            bindings.put("npc_name", EntityHighlight.wrap(otherNpcs.getFirst().name()));
            bindings.put("npc_name_2", EntityHighlight.wrap(otherNpcs.getFirst().name()));
        }

        // POI type
        if (!ctx.poiTypes().isEmpty()) {
            bindings.put("poi_type", ctx.poiTypes().get(random.nextInt(ctx.poiTypes().size())));
        }

        // Mob type: prefer the NPC's pre-generated quest binding so dialogue text
        // agrees with the POI spawn descriptor. If no binding exists yet, pick one
        // and persist it into the quest so the POI spawn path sees the same value.
        if (!ctx.mobTypes().isEmpty()) {
            String chosenMob;
            if (preGeneratedQuest != null
                    && preGeneratedQuest.getVariableBindings().containsKey("mob_type")) {
                chosenMob = preGeneratedQuest.getVariableBindings().get("mob_type");
            } else {
                chosenMob = ctx.mobTypes().get(random.nextInt(ctx.mobTypes().size()));
                if (preGeneratedQuest != null) {
                    preGeneratedQuest.getVariableBindings().put("mob_type", chosenMob);
                }
            }
            bindings.put("mob_type", chosenMob);
        }

        // Other settlement
        if (!ctx.nearbySettlementNames().isEmpty()) {
            bindings.put("other_settlement", EntityHighlight.wrap(
                ctx.nearbySettlementNames().get(random.nextInt(ctx.nearbySettlementNames().size()))));
        }

        // Tier 1: role variables
        bindings.put("self_role", roleDisplayName(npcRole));
        String referencedNpcName = EntityHighlight.stripMarkers(bindings.get("npc_name"));
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

        // Tone framing (valence-aware): dedup-aware so the same opener/closer
        // is not reused across the world until ~80% of the lane is exhausted.
        String bracket = dispositionBracket(disposition);
        FramingShape shape = FramingShape.roll(bracket, random);
        ValenceType entryValence = entry.valence();
        bindings.put("tone_opener", shape.hasOpener()
            ? topicPool.randomToneOpener(bracket, entryValence, dedup, random) + " "
            : "");
        bindings.put("tone_closer", shape.hasCloser()
            ? " " + topicPool.randomToneCloser(bracket, entryValence, dedup, random)
            : "");

        // Entry content: pre-resolve variables in pool text so nested tokens
        // like {subject_focus_the} inside entry.intro() get substituted.
        bindings.put("entry_intro", DialogueResolver.resolve(entry.intro(), bindings));
        if (!entry.reactions().isEmpty()) {
            bindings.put("entry_reaction", DialogueResolver.resolve(entry.reactions().getFirst(), bindings));
        }

        return bindings;
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
     * Delegates to {@link com.chonbosmods.settlement.NpcRecord#displayRole(String)}
     * which is the single source of truth for this mapping.
     */
    public static String roleDisplayName(String role) {
        return com.chonbosmods.settlement.NpcRecord.displayRole(role);
    }
}
