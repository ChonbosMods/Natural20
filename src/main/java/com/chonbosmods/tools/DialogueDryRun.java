package com.chonbosmods.tools;

import com.chonbosmods.dialogue.DispositionBracket;
import com.chonbosmods.dialogue.ValenceType;
import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.quest.DialogueResolver;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementType;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.topic.*;
import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CLI tool that runs the dialogue generation flow offline and outputs a human-readable
 * markdown preview of NPC smalltalk conversations. Smalltalk only: no quest generation.
 *
 * Usage:
 *   java DialogueDryRun [--settlement path] [--seed long] [--output path]
 */
public class DialogueDryRun {

    // Role-based topic budgets (mirrored from TopicGenerator)
    private static final int SOCIAL_MIN_TOPICS = 2;
    private static final int SOCIAL_MAX_TOPICS = 4;
    private static final int FUNCTIONAL_MIN_TOPICS = 0;
    private static final int FUNCTIONAL_MAX_TOPICS = 2;
    private static final Set<String> SOCIAL_ROLES = Set.of(
        "TavernKeeper", "ArtisanAlchemist", "ArtisanBlacksmith", "ArtisanCook", "Traveler"
    );

    private static final double RUMOR_RATIO = 0.4;

    private static final Map<String, String> TEMPLATE_LABELS = Map.ofEntries(
        Map.entry("danger", "Trouble"),
        Map.entry("sighting", "Sighting"),
        Map.entry("treasure", "Finds"),
        Map.entry("corruption", "Complaints"),
        Map.entry("conflict", "Disputes"),
        Map.entry("disappearance", "Missing Things"),
        Map.entry("migration", "Comings and Goings"),
        Map.entry("omen", "Old Sayings"),
        Map.entry("weather", "The Weather"),
        Map.entry("trade", "Trade"),
        Map.entry("craftsmanship", "Crafts"),
        Map.entry("community", "Around Town"),
        Map.entry("nature", "The Outdoors"),
        Map.entry("nostalgia", "Old Times"),
        Map.entry("curiosity", "Idle Thoughts"),
        Map.entry("festival", "Celebrations")
    );

    private static final List<String> RUMOR_DECK = List.of(
        "danger", "sighting", "treasure", "corruption", "conflict", "disappearance", "migration", "omen"
    );
    private static final List<String> SMALLTALK_DECK = List.of(
        "trade", "weather", "craftsmanship", "community", "nature", "nostalgia", "curiosity", "festival"
    );

    public static void main(String[] args) {
        // Parse CLI args
        String settlementPath = null;
        long seed = 12345;
        String outputPath = "devserver/dialogue_preview.md";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--settlement" -> { if (i + 1 < args.length) settlementPath = args[++i]; }
                case "--seed" -> { if (i + 1 < args.length) seed = Long.parseLong(args[++i]); }
                case "--output" -> { if (i + 1 < args.length) outputPath = args[++i]; }
            }
        }

        System.out.println("Dialogue Dry Run");
        System.out.println("  Seed: " + seed);
        System.out.println("  Output: " + outputPath);

        // Phase 1: Load
        System.out.println("Loading topic pools...");
        TopicPoolRegistry topicPool = new TopicPoolRegistry();
        topicPool.loadAll(null);

        System.out.println("Loading topic templates...");
        TopicTemplateRegistry templateRegistry = new TopicTemplateRegistry();
        templateRegistry.loadAll(null);

        // Load or build settlements
        SettlementRecord primary;
        SettlementRecord nearby;

        if (settlementPath != null) {
            System.out.println("Loading settlements from: " + settlementPath);
            SettlementRecord[] settlements = loadSettlements(Path.of(settlementPath));
            primary = settlements[0];
            nearby = settlements.length > 1 ? settlements[1] : buildNearbyFixture();
        } else {
            System.out.println("Using synthetic fixture settlements.");
            primary = buildPrimaryFixture();
            nearby = buildNearbyFixture();
        }

        System.out.println("Primary settlement: " + primary.getCellKey()
            + " (" + primary.getNpcs().size() + " NPCs)");
        System.out.println("Nearby settlement: " + nearby.getCellKey()
            + " (" + nearby.getNpcs().size() + " NPCs)");

        // Phase 2: Generate
        System.out.println("Generating dialogue graphs...");
        Map<String, DialogueGraph> graphs = generateSmalltalk(
            primary, nearby, topicPool, templateRegistry, seed);

        System.out.println("Generated " + graphs.size() + " dialogue graphs.");

        // Phase 3: Render
        System.out.println("Rendering markdown...");
        String markdown = renderMarkdown(primary, nearby, graphs, seed);

        // Write output
        try {
            Path out = Path.of(outputPath);
            Files.createDirectories(out.getParent());
            Files.writeString(out, markdown, StandardCharsets.UTF_8);
            System.out.println("Written to: " + out.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write output: " + e.getMessage());
            System.exit(1);
        }
    }

    // ---- Phase 2: Generate ----

    private static Map<String, DialogueGraph> generateSmalltalk(
            SettlementRecord primary, SettlementRecord nearby,
            TopicPoolRegistry topicPool, TopicTemplateRegistry templateRegistry,
            long seed) {

        List<NpcRecord> npcs = primary.getNpcs();
        Random random = new Random(seed);
        PercentageDedup dedup = new PercentageDedup();

        // Step 1: Roll topic budgets
        Map<String, Integer> topicBudgets = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            int min, max;
            if (SOCIAL_ROLES.contains(npc.getRole())) {
                min = SOCIAL_MIN_TOPICS;
                max = SOCIAL_MAX_TOPICS;
            } else {
                min = FUNCTIONAL_MIN_TOPICS;
                max = FUNCTIONAL_MAX_TOPICS;
            }
            int budget = min + random.nextInt(max - min + 1);
            topicBudgets.put(npc.getGeneratedName(), budget);
            System.out.println("  " + npc.getGeneratedName() + " (" + npc.getRole()
                + "): budget=" + budget);
        }

        // Step 2: Draw subjects (settlement-wide dedup)
        Set<String> usedSubjectValues = new LinkedHashSet<>();
        Map<String, List<SubjectFocus>> npcSubjects = new LinkedHashMap<>();

        int subjectIdx = 0;
        for (int npcIdx = 0; npcIdx < npcs.size(); npcIdx++) {
            NpcRecord npc = npcs.get(npcIdx);
            String npcName = npc.getGeneratedName();
            int budget = topicBudgets.get(npcName);

            Random deckRandom = new Random(seed ^ ((long) npcIdx * 31));
            int rumorCount = (int) Math.ceil(budget * RUMOR_RATIO);
            int smallTalkCount = budget - rumorCount;

            List<String> rumorDeck = new ArrayList<>(RUMOR_DECK);
            List<String> smalltalkDeck = new ArrayList<>(SMALLTALK_DECK);
            Collections.shuffle(rumorDeck, deckRandom);
            Collections.shuffle(smalltalkDeck, deckRandom);

            List<SubjectFocus> npcTopics = new ArrayList<>();

            for (int i = 0; i < rumorCount; i++) {
                String category = rumorDeck.get(i % rumorDeck.size());
                TopicPoolRegistry.SubjectEntry entry =
                    drawUniqueSubject(topicPool, category, usedSubjectValues, random);
                usedSubjectValues.add(entry.value());
                String subjectId = "subj_" + (subjectIdx++) + "_" + sanitize(entry.value());
                npcTopics.add(new SubjectFocus(subjectId, entry.value(), entry.plural(),
                    entry.proper(), entry.questEligible(), entry.concrete(),
                    entry.categories(), entry.poiType(), entry.questAffinities()));
            }

            for (int i = 0; i < smallTalkCount; i++) {
                String category = smalltalkDeck.get(i % smalltalkDeck.size());
                TopicPoolRegistry.SubjectEntry entry =
                    drawUniqueSubject(topicPool, category, usedSubjectValues, random);
                usedSubjectValues.add(entry.value());
                String subjectId = "subj_" + (subjectIdx++) + "_" + sanitize(entry.value());
                npcTopics.add(new SubjectFocus(subjectId, entry.value(), entry.plural(),
                    entry.proper(), entry.questEligible(), entry.concrete(),
                    entry.categories(), entry.poiType(), entry.questAffinities()));
            }

            npcSubjects.put(npcName, npcTopics);
        }

        // Step 3: Build assignments (no quests: hasQuest=false always)
        Map<String, List<TopicGraphBuilder.TopicAssignment>> npcAssignments = new LinkedHashMap<>();

        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = new ArrayList<>();
            for (SubjectFocus focus : npcSubjects.get(npcName)) {
                assignments.add(buildAssignment(
                    focus, npc, primary, nearby, random, dedup, topicPool, templateRegistry));
            }
            npcAssignments.put(npcName, assignments);
        }

        // Step 4: Build graphs
        Map<String, DialogueGraph> results = new LinkedHashMap<>();
        for (NpcRecord npc : npcs) {
            String npcName = npc.getGeneratedName();
            List<TopicGraphBuilder.TopicAssignment> assignments = npcAssignments.get(npcName);

            String greeting = topicPool.randomGreeting(random);
            String returnGreeting = topicPool.randomReturnGreeting(random);

            TopicGraphBuilder builder = new TopicGraphBuilder(
                npcName, npc.getDisposition(), greeting, returnGreeting,
                assignments, topicPool, random
            );
            DialogueGraph graph = builder.build();

            if (graph.validate()) {
                results.put(npcName, graph);
            } else {
                System.err.println("WARNING: Invalid dialogue graph for " + npcName);
            }
        }

        return results;
    }

    private static TopicGraphBuilder.TopicAssignment buildAssignment(
            SubjectFocus focus, NpcRecord npc,
            SettlementRecord primary, SettlementRecord nearby,
            Random random, PercentageDedup dedup,
            TopicPoolRegistry topicPool, TopicTemplateRegistry templateRegistry) {

        TopicTemplate template = templateRegistry.randomTemplateForSubject(
            focus.getCategories(), focus.isConcrete(), random);

        List<PoolEntry> pool = topicPool.getCoherentPool(template.id());
        PoolEntry entry;
        if (!pool.isEmpty()) {
            int idx = dedup.draw(template.id(), pool.size(), random);
            entry = pool.get(idx);
        } else {
            entry = new PoolEntry(0, "I had something on my mind, but it's slipped away.",
                List.of("Happens more often than I'd like to admit."),
                List.of("Getting old, I suppose."),
                null, ValenceType.NEUTRAL);
        }

        Skill skill = null;
        if (template.skills() != null && !template.skills().isEmpty()) {
            String skillName = template.skills().get(random.nextInt(template.skills().size()));
            try { skill = Skill.valueOf(skillName); }
            catch (IllegalArgumentException ignored) {}
        }

        Map<String, String> bindings = buildBindings(
            focus, npc.getGeneratedName(), npc.getRole(), npc.getDisposition(),
            primary, nearby, template, entry, dedup, topicPool, random);

        String label = TEMPLATE_LABELS.getOrDefault(template.id(), template.id());

        return new TopicGraphBuilder.TopicAssignment(
            focus.getSubjectId(), label,
            bindings, true, false, skill, template, entry
        );
    }

    private static Map<String, String> buildBindings(
            SubjectFocus focus, String npcName, String roleName,
            int disposition,
            SettlementRecord primary, SettlementRecord nearby,
            TopicTemplate template, PoolEntry entry,
            PercentageDedup dedup,
            TopicPoolRegistry topicPool, Random random) {

        Map<String, String> bindings = new HashMap<>();

        // No subject_focus bindings: generated smalltalk entries are self-contained.
        bindings.put("npc_name", npcName);
        bindings.put("other_settlement", capitalizeFirst(nearby.getCellKey().split("_")[0]));

        // Settlement name
        bindings.put("settlement_name", capitalizeFirst(primary.getCellKey().split("_")[0]));

        // Second NPC (use another NPC from the primary settlement if available)
        List<NpcRecord> allNpcs = primary.getNpcs();
        List<NpcRecord> otherNpcsForRef = allNpcs.stream()
            .filter(n -> !n.getGeneratedName().equals(npcName))
            .toList();
        if (otherNpcsForRef.size() >= 2) {
            bindings.put("npc_name_2", otherNpcsForRef.get(random.nextInt(otherNpcsForRef.size())).getGeneratedName());
        } else if (otherNpcsForRef.size() == 1) {
            bindings.put("npc_name_2", otherNpcsForRef.getFirst().getGeneratedName());
        }

        // POI type
        List<String> poiTypes = primary.getSettlementType().getPoiTypes();
        if (!poiTypes.isEmpty()) {
            bindings.put("poi_type", poiTypes.get(random.nextInt(poiTypes.size())));
        }

        // Mob type
        List<String> mobTypes = primary.getSettlementType().getMobTypes();
        if (!mobTypes.isEmpty()) {
            bindings.put("mob_type", mobTypes.get(random.nextInt(mobTypes.size())));
        }

        // Role variables
        bindings.put("self_role", TopicGenerator.roleDisplayName(roleName));
        String refNpcName = bindings.get("npc_name");
        if (refNpcName != null) {
            NpcRecord refNpc = primary.getNpcByName(refNpcName);
            if (refNpc != null) {
                bindings.put("npc_role", TopicGenerator.roleDisplayName(refNpc.getRole()));
            }
        }

        // Flavor pools
        bindings.put("food_type", dedup.drawFrom("food_types", topicPool.getFoodTypes(), random));
        bindings.put("crop_type", dedup.drawFrom("crop_types", topicPool.getCropTypes(), random));
        bindings.put("wildlife_type", dedup.drawFrom("wildlife_types", topicPool.getWildlifeTypes(), random));
        String poiKey = bindings.getOrDefault("poi_type", "general");
        bindings.put("resource_type", dedup.drawFrom(
            "resource_types_" + poiKey,
            topicPool.getResourceTypes(poiKey),
            random));

        // Drop-ins
        bindings.put("time_ref", dedup.drawFrom("time_refs", topicPool.getTimeRefs(), random));
        bindings.put("direction", dedup.drawFrom("directions", topicPool.getDirections(), random));

        // Tone framing
        String bracket = DispositionBracket.textPoolFromDisposition(disposition);
        FramingShape shape = FramingShape.roll(bracket, random);
        ValenceType entryValence = entry.valence();
        bindings.put("tone_opener", shape.hasOpener()
            ? topicPool.randomToneOpener(bracket, entryValence, random) + " " : "");
        bindings.put("tone_closer", shape.hasCloser()
            ? " " + topicPool.randomToneCloser(bracket, entryValence, random) : "");

        // Entry content
        bindings.put("entry_intro", DialogueResolver.resolve(entry.intro(), bindings));
        if (!entry.reactions().isEmpty()) {
            bindings.put("entry_reaction",
                DialogueResolver.resolve(entry.reactions().getFirst(), bindings));
        }

        return bindings;
    }

    // ---- Phase 3: Render ----

    private static String renderMarkdown(
            SettlementRecord primary, SettlementRecord nearby,
            Map<String, DialogueGraph> graphs, long seed) {

        StringBuilder md = new StringBuilder();
        String settlementName = primary.getCellKey().split("_")[0];
        settlementName = capitalizeFirst(settlementName);
        String nearbyName = nearby.getCellKey().split("_")[0];
        nearbyName = capitalizeFirst(nearbyName);

        md.append("# Dialogue Preview: ").append(settlementName).append("\n");
        md.append("*Seed: ").append(seed).append(" | Generated: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
            .append("*\n");
        md.append("*Nearby settlement: ").append(nearbyName).append("*\n");

        for (NpcRecord npc : primary.getNpcs()) {
            String npcName = npc.getGeneratedName();
            DialogueGraph graph = graphs.get(npcName);
            if (graph == null) continue;

            md.append("\n---\n\n");
            md.append("## ").append(npcName)
                .append(" (").append(npc.getRole())
                .append(", disposition: ").append(npc.getDisposition())
                .append(")\n\n");

            // Greeting
            DialogueNode greetingNode = graph.getNode(graph.greetingNodeId());
            if (greetingNode instanceof DialogueNode.DialogueTextNode greet) {
                md.append("**Greeting:** \"").append(greet.speakerText()).append("\"\n");
            }

            DialogueNode returnNode = graph.getNode(graph.returnGreetingNodeId());
            if (returnNode instanceof DialogueNode.DialogueTextNode ret) {
                md.append("**Return greeting:** \"").append(ret.speakerText()).append("\"\n");
            }

            // Topics
            for (TopicDefinition topic : graph.topics()) {
                md.append("\n### Topic: ").append(topic.label()).append("\n");

                DialogueNode entryNode = graph.getNode(topic.entryNodeId());
                renderNode(md, graph, entryNode, 0, new HashSet<>());
            }
        }

        return md.toString();
    }

    private static void renderNode(
            StringBuilder md, DialogueGraph graph, DialogueNode node,
            int depth, Set<String> visited) {

        String indent = "  ".repeat(depth);

        if (node instanceof DialogueNode.DialogueTextNode text) {
            md.append(indent).append("> **NPC:** \"").append(text.speakerText()).append("\"\n");

            if (text.responses().isEmpty()) return;

            md.append("\n").append(indent).append("**Player responses:**\n");

            for (ResponseOption resp : text.responses()) {
                if (resp.skillCheckRef() != null) {
                    // This is a skill check response: render the check node inline
                    renderSkillCheckResponse(md, graph, resp, depth, visited);
                } else if (resp.responseType() == ResponseType.POSTURE) {
                    // Posture (explore) response
                    md.append(indent).append("- *(explore)* ");
                    String targetId = resp.targetNodeId();
                    if (targetId != null && !visited.contains(targetId)) {
                        visited.add(targetId);
                        DialogueNode target = graph.getNode(targetId);
                        if (target instanceof DialogueNode.DialogueTextNode targetText) {
                            md.append("→ **Detail:**\n");
                            renderNode(md, graph, targetText, depth + 1, visited);
                        } else if (target != null) {
                            md.append("→ *(node: ").append(targetId).append(")*\n");
                        } else {
                            md.append("\n");
                        }
                    } else {
                        md.append("\n");
                    }
                } else {
                    // Authored/decisive response
                    String label = resp.displayText() != null ? resp.displayText() : "(continue)";
                    md.append(indent).append("- \"").append(label).append("\"");
                    String targetId = resp.targetNodeId();
                    if (targetId != null && !visited.contains(targetId)) {
                        visited.add(targetId);
                        DialogueNode target = graph.getNode(targetId);
                        if (target instanceof DialogueNode.DialogueTextNode targetText) {
                            md.append(" →\n");
                            renderNode(md, graph, targetText, depth + 1, visited);
                        } else if (target instanceof DialogueNode.ActionNode action) {
                            md.append(" → *(action)*\n");
                        } else {
                            md.append("\n");
                        }
                    } else {
                        md.append("\n");
                    }
                }
            }
        } else if (node instanceof DialogueNode.SkillCheckNode check) {
            renderSkillCheckNode(md, graph, check, depth, visited);
        } else if (node instanceof DialogueNode.TerminalNode) {
            md.append(indent).append("*(end)*\n");
        }
    }

    private static void renderSkillCheckResponse(
            StringBuilder md, DialogueGraph graph, ResponseOption resp,
            int depth, Set<String> visited) {

        String indent = "  ".repeat(depth);
        String targetId = resp.skillCheckRef();
        DialogueNode checkNode = graph.getNode(targetId);

        if (checkNode instanceof DialogueNode.SkillCheckNode check) {
            String skillDisplay = check.skill() != null ? check.skill().displayName() : "Unknown";
            String statPrefix = resp.statPrefix() != null ? resp.statPrefix() : "";

            md.append(indent).append("- **[").append(statPrefix).append("] ")
                .append(skillDisplay).append(" Check** (DC ").append(check.baseDC()).append(") →\n");

            // Pass
            if (!visited.contains(check.passNodeId())) {
                visited.add(check.passNodeId());
                DialogueNode passNode = graph.getNode(check.passNodeId());
                if (passNode instanceof DialogueNode.DialogueTextNode passText) {
                    md.append(indent).append("  - **Pass:** \"").append(passText.speakerText()).append("\"\n");
                }
            }

            // Fail
            if (!visited.contains(check.failNodeId())) {
                visited.add(check.failNodeId());
                DialogueNode failNode = graph.getNode(check.failNodeId());
                if (failNode instanceof DialogueNode.DialogueTextNode failText) {
                    md.append(indent).append("  - **Fail:** \"").append(failText.speakerText()).append("\"\n");
                }
            }
        } else {
            // Fallback: render as normal response
            String label = resp.displayText() != null ? resp.displayText() : "(check)";
            md.append(indent).append("- \"").append(label).append("\" → *(skill check)*\n");
        }
    }

    private static void renderSkillCheckNode(
            StringBuilder md, DialogueGraph graph, DialogueNode.SkillCheckNode check,
            int depth, Set<String> visited) {

        String indent = "  ".repeat(depth);
        String skillDisplay = check.skill() != null ? check.skill().displayName() : "Unknown";

        md.append(indent).append("**Skill Check:** ").append(skillDisplay)
            .append(" (DC ").append(check.baseDC()).append(")\n");

        if (!visited.contains(check.passNodeId())) {
            visited.add(check.passNodeId());
            DialogueNode passNode = graph.getNode(check.passNodeId());
            if (passNode instanceof DialogueNode.DialogueTextNode passText) {
                md.append(indent).append("  - **Pass:** \"").append(passText.speakerText()).append("\"\n");
            }
        }

        if (!visited.contains(check.failNodeId())) {
            visited.add(check.failNodeId());
            DialogueNode failNode = graph.getNode(check.failNodeId());
            if (failNode instanceof DialogueNode.DialogueTextNode failText) {
                md.append(indent).append("  - **Fail:** \"").append(failText.speakerText()).append("\"\n");
            }
        }
    }

    // ---- Fixtures ----

    private static SettlementRecord buildPrimaryFixture() {
        SettlementRecord settlement = new SettlementRecord(
            "thornfield_0", UUID.randomUUID(), 100, 64, 200, SettlementType.TOWN);

        addNpc(settlement, "Marta Greaves", "TavernKeeper", 65);
        addNpc(settlement, "Rodwin Ash", "ArtisanBlacksmith", 55);
        addNpc(settlement, "Elia Strand", "Villager", 72);
        addNpc(settlement, "Garret Hollow", "Guard", 40);
        addNpc(settlement, "Bren Oakes", "Villager", 60);
        addNpc(settlement, "Sienna Moss", "ArtisanCook", 78);

        return settlement;
    }

    private static SettlementRecord buildNearbyFixture() {
        SettlementRecord settlement = new SettlementRecord(
            "briggsham_0", UUID.randomUUID(), 500, 64, 600, SettlementType.TOWN);

        addNpc(settlement, "Aldric Fenn", "TavernKeeper", 70);
        addNpc(settlement, "Hanna Birch", "ArtisanAlchemist", 58);
        addNpc(settlement, "Corwin Dray", "Villager", 45);
        addNpc(settlement, "Petra Holt", "Guard", 52);
        addNpc(settlement, "Lena Marsh", "Villager", 75);
        addNpc(settlement, "Tomas Wren", "ArtisanCook", 63);

        return settlement;
    }

    private static void addNpc(SettlementRecord settlement, String name, String role, int disposition) {
        NpcRecord npc = new NpcRecord(role, UUID.randomUUID(),
            100, 64, 200, 0, 0, 0, 8.0, name);
        npc.setDisposition(disposition);
        settlement.getNpcs().add(npc);
    }

    // ---- Settlement loading ----

    private static SettlementRecord[] loadSettlements(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, SettlementRecord[].class);
        } catch (IOException e) {
            System.err.println("Failed to load settlements from " + path + ": " + e.getMessage());
            System.exit(1);
            return new SettlementRecord[0]; // unreachable
        }
    }

    // ---- Utilities ----

    private static TopicPoolRegistry.SubjectEntry drawUniqueSubject(
            TopicPoolRegistry topicPool, String targetCategory,
            Set<String> usedValues, Random random) {
        for (int attempt = 0; attempt < 3; attempt++) {
            TopicPoolRegistry.SubjectEntry entry =
                topicPool.randomSubjectForCategoryExcluding(targetCategory, usedValues, random);
            if (!usedValues.contains(entry.value())) return entry;
        }
        return topicPool.randomSubjectForCategory(targetCategory, random);
    }

    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
