package com.chonbosmods.topic;

import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.quest.DialogueResolver;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.stats.Stat;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Assembles a {@link DialogueGraph} from resolved template data for a single NPC.
 * Produces structurally identical graphs to what {@link com.chonbosmods.dialogue.DialogueLoader}
 * produces from hand-authored JSON, so that ConversationSession can process them unchanged.
 */
public class TopicGraphBuilder {

    public record TopicAssignment(
        String subjectId,
        String labelPattern,
        Map<String, String> bindings,
        boolean startVisible,
        boolean hasQuest,
        @Nullable Skill skill,
        @Nullable TopicTemplate template,
        @Nullable PoolEntry entry
    ) {}

    // --- Topic shape probabilities ---
    /** Chance each detail branch is included (rolled independently per detail). */
    private static final double DETAIL_INCLUDE_CHANCE = 0.70;
    /** Chance a detail branch gets a reaction follow-up (rolled per included detail). */
    private static final double REACTION_FOLLOWUP_CHANCE = 0.30;
    /** Chance the stat check appears when the entry has one authored. */
    private static final double STAT_CHECK_CHANCE = 0.60;
    /** Max detail branches on the entry node (hard cap before probability rolls). */
    private static final int MAX_DETAILS = 2;

    // --- Stat check tuning ---
    private static final int STAT_CHECK_DC_MIN = 8;
    private static final int STAT_CHECK_DC_MAX = 16;
    private static final int STAT_CHECK_PASS_DISPOSITION = 5;
    private static final int STAT_CHECK_FAIL_DISPOSITION = -3;

    private final String npcId;
    private final int defaultDisposition;
    private final String greetingText;
    private final String returnGreetingText;
    private final List<TopicAssignment> assignments;
    private final TopicPoolRegistry topicPool;
    private final Random random;
    private final PromptGroupRegistry promptGroups;

    public TopicGraphBuilder(
            String npcId,
            int defaultDisposition,
            String greetingText,
            String returnGreetingText,
            List<TopicAssignment> assignments,
            TopicPoolRegistry topicPool,
            Random random,
            PromptGroupRegistry promptGroups
    ) {
        this.npcId = npcId;
        this.defaultDisposition = defaultDisposition;
        this.greetingText = greetingText;
        this.returnGreetingText = returnGreetingText;
        this.assignments = assignments;
        this.topicPool = topicPool;
        this.random = random;
        this.promptGroups = promptGroups;
    }

    public DialogueGraph build() {
        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        List<TopicDefinition> topics = new ArrayList<>();

        // Greeting nodes: plain speech with no responses
        String greetingNodeId = "greeting";
        String returnGreetingNodeId = "return_greeting";

        nodes.put(greetingNodeId, new DialogueNode.DialogueTextNode(
            greetingText, null, List.of(), List.of(), false, false
        ));
        nodes.put(returnGreetingNodeId, new DialogueNode.DialogueTextNode(
            returnGreetingText, null, List.of(), List.of(), false, false
        ));

        // Build each topic
        int sortOrder = 0;
        for (TopicAssignment assignment : assignments) {
            buildTopic(assignment, sortOrder++, nodes, topics);
        }

        return new DialogueGraph(
            npcId, defaultDisposition, greetingNodeId, returnGreetingNodeId, topics, nodes
        );
    }

    /**
     * Build a topic graph from the coherent-pool format (PoolEntry + TopicTemplate).
     * Structure: entry node with detail branches, optional stat check, and decisive ending.
     */
    private void buildTopic(
            TopicAssignment assignment,
            int sortOrder,
            Map<String, DialogueNode> nodes,
            List<TopicDefinition> topics
    ) {
        String subjectId = assignment.subjectId();
        Map<String, String> bindings = assignment.bindings();
        TopicTemplate template = assignment.template();
        PoolEntry entry = assignment.entry();

        String entryNodeId = subjectId + "_entry";

        // Resolve intro
        String introPattern = template.introPattern() != null ? template.introPattern() : "{entry_intro}";
        String introText = DialogueResolver.resolve(introPattern, bindings);

        List<ResponseOption> entryResponses = new ArrayList<>();

        if (!assignment.hasQuest()) {
            // Resolve all reactions once for reactionPool sharing
            List<String> resolvedReactions = entry.reactions().stream()
                .map(r -> DialogueResolver.resolve(r, bindings))
                .toList();

            // Detail branches: each detail has an independent chance of appearing.
            // Keeps topic shape varied: some topics get 0 details, most get 1-2.
            int detailCap = Math.min(entry.details().size(), MAX_DETAILS);
            for (int i = 0; i < detailCap; i++) {
                if (random.nextDouble() >= DETAIL_INCLUDE_CHANCE) continue;

                String detailNodeId = subjectId + "_detail_" + i;
                String detailResponseId = subjectId + "_resp_detail_" + i;

                String detailText = DialogueResolver.resolve(entry.details().get(i), bindings);

                // Independent chance of a reaction follow-up on this detail
                List<ResponseOption> detailChildResponses = new ArrayList<>();
                if (!resolvedReactions.isEmpty() && random.nextDouble() < REACTION_FOLLOWUP_CHANCE) {
                    String reactionNodeId = subjectId + "_reaction_" + i;
                    String reactionResponseId = subjectId + "_resp_reaction_" + i;

                    String reactionFallback = resolvedReactions.getFirst();
                    nodes.put(reactionNodeId, new DialogueNode.DialogueTextNode(
                        reactionFallback, resolvedReactions, List.of(), List.of(), false, false
                    ));

                    String reactionPrompt = pickPromptFromGroups(template.reactionPrompts());
                    detailChildResponses.add(new ResponseOption(
                        reactionResponseId, reactionPrompt, null, reactionNodeId,
                        ResponseMode.EXPLORATORY, null, null, null, null
                    ));
                }

                // Detail node
                nodes.put(detailNodeId, new DialogueNode.DialogueTextNode(
                    detailText, null, detailChildResponses, List.of(), false, false
                ));

                // Detail response option on entry node
                String detailPrompt = pickPromptFromGroups(template.detailPrompts());
                entryResponses.add(new ResponseOption(
                    detailResponseId, detailPrompt, null, detailNodeId,
                    ResponseMode.EXPLORATORY, null, null, null, null
                ));
            }

            // Stat check: independent chance even when the entry has one authored
            if (entry.statCheck() != null && assignment.skill() != null
                    && random.nextDouble() < STAT_CHECK_CHANCE) {
                Skill skill = assignment.skill();
                Stat stat = skill.getStat();

                int baseDC = STAT_CHECK_DC_MIN + random.nextInt(STAT_CHECK_DC_MAX - STAT_CHECK_DC_MIN + 1);

                String checkNodeId = subjectId + "_skill_check";
                String passNodeId = subjectId + "_skill_pass";
                String failNodeId = subjectId + "_skill_fail";
                String checkResponseId = subjectId + "_resp_skill_check";

                String passText = DialogueResolver.resolve(entry.statCheck().pass(), bindings);
                String failText = DialogueResolver.resolve(entry.statCheck().fail(), bindings);

                // Pass node: disposition bonus, exhausts topic
                nodes.put(passNodeId, new DialogueNode.DialogueTextNode(
                    passText, null, List.of(),
                    List.of(Map.of("type", "MODIFY_DISPOSITION", "amount",
                        String.valueOf(STAT_CHECK_PASS_DISPOSITION))),
                    true, false
                ));

                // Fail node: disposition penalty, exhausts topic
                nodes.put(failNodeId, new DialogueNode.DialogueTextNode(
                    failText, null, List.of(),
                    List.of(Map.of("type", "MODIFY_DISPOSITION", "amount",
                        String.valueOf(STAT_CHECK_FAIL_DISPOSITION))),
                    true, false
                ));

                // Skill check node
                nodes.put(checkNodeId, new DialogueNode.SkillCheckNode(
                    skill, null, baseDC, true, passNodeId, failNodeId, List.of()
                ));

                // Skill check response on entry node
                // displayText is just the skill name; the UI prepends a color-coded [STAT] bracket from statPrefix
                entryResponses.add(new ResponseOption(
                    checkResponseId,
                    skill.displayName(),
                    null,
                    checkNodeId,
                    ResponseMode.EXPLORATORY,
                    null,
                    checkNodeId,
                    stat.name(),
                    null
                ));
            }

            // No decisive response: topic exhausts naturally when all exploratories
            // are grayed (ConversationSession.returnCheck handles this).
            // UNLOCK_TOPIC fires on entry so the topic is globally available.
        } else {
            // Quest-bearer: explorable chain through dialogueChunks (intro -> plotStep -> outro)
            // with accept/decline at every level
            String questExpo = bindings.getOrDefault("quest_exposition", "I need your help.");
            String questSummary = bindings.getOrDefault("quest_objective_summary", "");
            if (!questSummary.isEmpty()) {
                introText = questExpo + " You'll need to " + questSummary + ".";
            } else {
                introText = questExpo;
            }

            String actionNodeId = subjectId + "_action_decisive";
            String confirmNodeId = subjectId + "_confirm_decisive";
            String declineNodeId = subjectId + "_decline";

            String confirmText = bindings.getOrDefault("quest_accept_response", "Good. Be careful out there.");

            nodes.put(actionNodeId, new DialogueNode.ActionNode(
                List.of(
                    Map.of("type", "GIVE_QUEST"),
                    Map.of("type", "UNLOCK_TOPIC", "topicId", subjectId, "scope", "GLOBAL")
                ), confirmNodeId, List.of(), false
            ));
            nodes.put(confirmNodeId, new DialogueNode.DialogueTextNode(
                confirmText, null, List.of(), List.of(), true, false
            ));
            nodes.put(declineNodeId, new DialogueNode.DialogueTextNode(
                "I understand. Perhaps another time.", null, List.of(),
                List.of(Map.of("type", "UNLOCK_TOPIC", "topicId", subjectId, "scope", "GLOBAL")),
                true, false
            ));

            // Build explorable chain if plotStep and outro are available
            String plotStep = bindings.get("quest_plot_step");
            String outro = bindings.get("quest_outro");

            if (plotStep != null && !plotStep.isBlank()) {
                // Entry -> [explore] plotStep node -> [explore] outro node (if available)
                // Accept/decline available at every level

                String plotNodeId = subjectId + "_plot";
                List<ResponseOption> plotResponses = new ArrayList<>();

                if (outro != null && !outro.isBlank()) {
                    // Plot -> outro chain
                    String outroNodeId = subjectId + "_outro";
                    List<ResponseOption> outroResponses = new ArrayList<>();
                    outroResponses.add(new ResponseOption(
                        subjectId + "_resp_outro_accept", "[Accept] I will handle it.", null, actionNodeId,
                        ResponseMode.DECISIVE, null, null, null, null
                    ));
                    outroResponses.add(new ResponseOption(
                        subjectId + "_resp_outro_decline", "[Decline] Maybe not right now.", null, declineNodeId,
                        ResponseMode.DECISIVE, null, null, null, null
                    ));
                    nodes.put(outroNodeId, new DialogueNode.DialogueTextNode(
                        outro, null, outroResponses, List.of(), false, false
                    ));

                    plotResponses.add(new ResponseOption(
                        subjectId + "_resp_outro", "Is there anything else?", null, outroNodeId,
                        ResponseMode.EXPLORATORY, null, null, null, null
                    ));
                }

                plotResponses.add(new ResponseOption(
                    subjectId + "_resp_plot_accept", "[Accept] I will handle it.", null, actionNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
                plotResponses.add(new ResponseOption(
                    subjectId + "_resp_plot_decline", "[Decline] Maybe not right now.", null, declineNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));

                nodes.put(plotNodeId, new DialogueNode.DialogueTextNode(
                    plotStep, null, plotResponses, List.of(), false, false
                ));

                // Exploratory link from entry to plot
                entryResponses.add(new ResponseOption(
                    subjectId + "_resp_plot", "What happened?", null, plotNodeId,
                    ResponseMode.EXPLORATORY, null, null, null, null
                ));
            }

            // Accept/decline on entry node (always present)
            entryResponses.add(new ResponseOption(
                subjectId + "_resp_accept", "[Accept] I will handle it.", null, actionNodeId,
                ResponseMode.DECISIVE, null, null, null, null
            ));
            entryResponses.add(new ResponseOption(
                subjectId + "_resp_decline", "[Decline] Maybe not right now.", null, declineNodeId,
                ResponseMode.DECISIVE, null, null, null, null
            ));
        }

        // Entry node: UNLOCK_TOPIC on enter so the topic is globally available
        List<Map<String, String>> entryOnEnter = assignment.hasQuest()
            ? List.of()
            : List.of(Map.of("type", "UNLOCK_TOPIC", "topicId", subjectId, "scope", "GLOBAL"));
        nodes.put(entryNodeId, new DialogueNode.DialogueTextNode(
            introText, null, entryResponses, entryOnEnter, false, false
        ));

        // Topic definition
        String resolvedLabel = capitalizeFirst(DialogueResolver.resolve(assignment.labelPattern(), bindings));
        String recapText = (!entry.reactions().isEmpty())
            ? DialogueResolver.resolve(entry.reactions().getFirst(), bindings)
            : introText;

        topics.add(new TopicDefinition(
            subjectId, resolvedLabel, entryNodeId,
            TopicScope.GLOBAL, null, assignment.startVisible(),
            null, sortOrder, recapText, assignment.hasQuest()
        ));
    }

    /**
     * Pick a random prompt string from named prompt groups.
     * Falls back to "Tell me more." if no groups or no prompts found.
     */
    private String pickPromptFromGroups(@Nullable List<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) return "Tell me more.";
        String group = groupNames.get(random.nextInt(groupNames.size()));
        return promptGroups.random(group, random);
    }

    private static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
