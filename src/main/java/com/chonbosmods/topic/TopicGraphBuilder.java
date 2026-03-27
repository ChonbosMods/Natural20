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
        TopicTemplate.Perspective perspective,
        Map<String, String> bindings,
        boolean startVisible,
        boolean hasQuest,
        @Nullable TopicTemplate.SkillCheckDef skillCheckDef,
        TopicCategory category
    ) {}

    private static final double STAT_CHECK_CHANCE = 0.25;
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
    private final Set<String> usedDeepeners;

    public TopicGraphBuilder(
            String npcId,
            int defaultDisposition,
            String greetingText,
            String returnGreetingText,
            List<TopicAssignment> assignments,
            TopicPoolRegistry topicPool,
            Random random,
            Set<String> usedDeepeners
    ) {
        this.npcId = npcId;
        this.defaultDisposition = defaultDisposition;
        this.greetingText = greetingText;
        this.returnGreetingText = returnGreetingText;
        this.assignments = assignments;
        this.topicPool = topicPool;
        this.random = random;
        this.usedDeepeners = usedDeepeners;
    }

    public DialogueGraph build() {
        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        List<TopicDefinition> topics = new ArrayList<>();

        // Greeting nodes: plain speech with no responses
        String greetingNodeId = "greeting";
        String returnGreetingNodeId = "return_greeting";

        nodes.put(greetingNodeId, new DialogueNode.DialogueTextNode(
            greetingText, List.of(), List.of(), false, false
        ));
        nodes.put(returnGreetingNodeId, new DialogueNode.DialogueTextNode(
            returnGreetingText, List.of(), List.of(), false, false
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

    private void buildTopic(
            TopicAssignment assignment,
            int sortOrder,
            Map<String, DialogueNode> nodes,
            List<TopicDefinition> topics
    ) {
        String subjectId = assignment.subjectId();
        Map<String, String> bindings = assignment.bindings();
        TopicTemplate.Perspective perspective = assignment.perspective();

        String entryNodeId = subjectId + "_entry";

        // Resolve intro text
        String introText = DialogueResolver.resolve(perspective.intro(), bindings);

        // Build response options for the entry node
        List<ResponseOption> entryResponses = new ArrayList<>();

        // Exploratory branches: skip for quest-bearing topics (accept/decline only)
        List<String> l1NodeIds = List.of();
        if (!assignment.hasQuest()) {
            if (perspective.usesIntents()) {
                l1NodeIds = buildIntentBranches(
                    perspective.intents(), subjectId, perspective.deepenerResponse(),
                    bindings, nodes, entryResponses
                );
            } else {
                buildExploratories(perspective.exploratories(), subjectId, "", bindings, nodes, entryResponses);
            }
        }

        // Skill check injection: non-quest topics with a skillCheckDef get a 25% chance
        if (assignment.skillCheckDef() != null && !assignment.hasQuest()
                && random.nextDouble() < STAT_CHECK_CHANCE) {
            if (perspective.usesIntents() && !l1NodeIds.isEmpty()) {
                injectSkillCheckOnNode(assignment, subjectId, bindings, nodes,
                    l1NodeIds.get(random.nextInt(l1NodeIds.size())));
            } else {
                injectSkillCheck(assignment, subjectId, bindings, perspective, nodes);
            }
        }

        // Decisive branch
        TopicTemplate.FollowUp decisive = perspective.decisive();
        List<Map<String, String>> entryOnEnter = List.of();

        if (decisive != null) {
            String prompt = DialogueResolver.resolve(decisive.prompt(), bindings);
            String responseId = subjectId + "_resp_decisive";

            if (assignment.hasQuest()) {
                // Quest-bearing: accept triggers GIVE_QUEST + UNLOCK_TOPIC, decline just unlocks
                String actionNodeId = subjectId + "_action_decisive";
                String confirmNodeId = subjectId + "_confirm_decisive";
                String terminalNodeId = subjectId + "_terminal";
                String declineNodeId = subjectId + "_decline";

                String confirmText = DialogueResolver.resolve(decisive.response(), bindings);

                List<Map<String, String>> acceptActions = List.of(
                    Map.of("type", "GIVE_QUEST"),
                    Map.of("type", "UNLOCK_TOPIC", "topicId", subjectId, "scope", "GLOBAL")
                );
                List<Map<String, String>> declineActions = List.of(
                    Map.of("type", "UNLOCK_TOPIC", "topicId", subjectId, "scope", "GLOBAL")
                );

                nodes.put(actionNodeId, new DialogueNode.ActionNode(
                    acceptActions, confirmNodeId, List.of(), false
                ));
                nodes.put(confirmNodeId, new DialogueNode.DialogueTextNode(
                    confirmText, List.of(), List.of(), true, false
                ));
                nodes.put(terminalNodeId, new DialogueNode.TerminalNode(List.of(), false));
                nodes.put(declineNodeId, new DialogueNode.DialogueTextNode(
                    "I understand. Perhaps another time.", List.of(), declineActions, true, false
                ));

                entryResponses.add(new ResponseOption(
                    responseId, "[Accept] " + prompt, null, actionNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
                String declineResponseId = subjectId + "_resp_decline";
                entryResponses.add(new ResponseOption(
                    declineResponseId, "[Decline] Maybe not right now.", null, declineNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
            } else {
                // Non-quest decisive: DIALOGUE with exhaustsTopic and UNLOCK_TOPIC onEnter
                String decisiveNodeId = subjectId + "_decisive";
                String responseText = DialogueResolver.resolve(decisive.response(), bindings);

                List<Map<String, String>> decisiveOnEnter = List.of(
                    Map.of("type", "UNLOCK_TOPIC", "topicId", subjectId, "scope", "GLOBAL")
                );

                nodes.put(decisiveNodeId, new DialogueNode.DialogueTextNode(
                    responseText, List.of(), decisiveOnEnter, true, false
                ));

                entryResponses.add(new ResponseOption(
                    responseId, prompt, null, decisiveNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
            }
        } else if (!assignment.hasQuest()) {
            // Non-quest topics without a decisive: fire UNLOCK_TOPIC on entry node's onEnter
            entryOnEnter = List.of(
                Map.of("type", "UNLOCK_TOPIC", "topicId", subjectId, "scope", "GLOBAL")
            );
        }

        // Entry dialogue node
        nodes.put(entryNodeId, new DialogueNode.DialogueTextNode(
            introText, entryResponses, entryOnEnter, false, false
        ));

        // Topic definition
        String resolvedLabel = DialogueResolver.resolve(assignment.labelPattern(), bindings);
        resolvedLabel = capitalizeFirst(resolvedLabel);

        DialogueCondition condition = null;
        if (!assignment.startVisible()) {
            condition = new DialogueCondition(
                "TOPIC_LEARNED",
                Map.of("topicId", subjectId),
                null, null
            );
        }

        // Build recap text: deepener response, last exploratory/intent response, or intro
        String recapText;
        if (decisive != null) {
            recapText = DialogueResolver.resolve(decisive.response(), bindings);
        } else if (perspective.usesIntents()) {
            if (perspective.deepenerResponse() != null) {
                recapText = DialogueResolver.resolve(perspective.deepenerResponse(), bindings);
            } else {
                var intents = perspective.intents();
                recapText = DialogueResolver.resolve(intents.getLast().response(), bindings);
            }
        } else if (!perspective.exploratories().isEmpty()) {
            TopicTemplate.FollowUp lastExp = perspective.exploratories().getLast();
            recapText = DialogueResolver.resolve(lastExp.response(), bindings);
        } else {
            recapText = introText;
        }

        topics.add(new TopicDefinition(
            subjectId,
            resolvedLabel,
            entryNodeId,
            TopicScope.GLOBAL,
            condition,
            assignment.startVisible(),
            null,
            sortOrder,
            recapText,
            assignment.hasQuest()
        ));
    }

    /**
     * Recursively build exploratory nodes and responses.
     * Each exploratory creates a dialogue node. If it has child exploratories,
     * those become response options on that node instead of it being a leaf.
     */
    private void buildExploratories(
            List<TopicTemplate.FollowUp> exploratories,
            String subjectId,
            String prefix,
            Map<String, String> bindings,
            Map<String, DialogueNode> nodes,
            List<ResponseOption> parentResponses
    ) {
        for (int i = 0; i < exploratories.size(); i++) {
            TopicTemplate.FollowUp followUp = exploratories.get(i);
            String expNodeId = subjectId + "_exp" + prefix + "_" + i;
            String responseId = subjectId + "_resp_exp" + prefix + "_" + i;

            String prompt = DialogueResolver.resolve(followUp.prompt(), bindings);
            String response = DialogueResolver.resolve(followUp.response(), bindings);

            List<ResponseOption> childResponses = new ArrayList<>();
            if (!followUp.exploratories().isEmpty()) {
                // Recurse: this node gets child response options
                buildExploratories(
                    followUp.exploratories(), subjectId, prefix + "_" + i,
                    bindings, nodes, childResponses
                );
            }

            nodes.put(expNodeId, new DialogueNode.DialogueTextNode(
                response, childResponses, List.of(), false, false
            ));

            parentResponses.add(new ResponseOption(
                responseId, prompt, null, expNodeId,
                ResponseMode.EXPLORATORY, null, null, null, null
            ));
        }
    }

    /**
     * Build exploratory nodes from intent slots.
     * Samples up to maxL1 intents, draws prompts from pools, and optionally
     * attaches L2 deepener nodes for intents that deepen.
     *
     * @return list of created L1 node IDs (for skill check injection)
     */
    private List<String> buildIntentBranches(
            List<TopicTemplate.IntentSlot> intentSlots,
            String subjectId,
            @Nullable String perspectiveDeepenerResponse,
            Map<String, String> bindings,
            Map<String, DialogueNode> nodes,
            List<ResponseOption> parentResponses
    ) {
        int maxL1 = topicPool.getMaxL1();
        List<TopicTemplate.IntentSlot> sampled;

        if (intentSlots.size() <= maxL1) {
            sampled = new ArrayList<>(intentSlots);
        } else {
            sampled = new ArrayList<>(intentSlots);
            Collections.shuffle(sampled, random);
            sampled = sampled.subList(0, maxL1);
        }

        List<String> l1NodeIds = new ArrayList<>();

        for (int i = 0; i < sampled.size(); i++) {
            TopicTemplate.IntentSlot slot = sampled.get(i);
            TopicPoolRegistry.IntentDef def = topicPool.getIntentDef(slot.intent());

            String expNodeId = subjectId + "_exp_" + i;
            String responseId = subjectId + "_resp_exp_" + i;

            // Draw prompt from intent pool
            String prompt = topicPool.randomPromptForIntent(slot.intent(), random);

            // Resolve NPC response from bindings
            String response = DialogueResolver.resolve(slot.response(), bindings);

            List<ResponseOption> childResponses = new ArrayList<>();

            // If this intent deepens, attach an L2 node
            if (def != null && def.deepens()) {
                String deepNodeId = subjectId + "_exp_" + i + "_deep";
                String deepResponseId = subjectId + "_resp_exp_" + i + "_deep";

                // Draw deepener prompt from generic pool with dedup
                String deepPrompt = topicPool.randomDeepenerExcluding(usedDeepeners, random);
                usedDeepeners.add(deepPrompt);

                // Resolve L2 NPC response: per-intent override > perspective default
                String deepResponsePattern = slot.deepenerResponse() != null
                    ? slot.deepenerResponse() : perspectiveDeepenerResponse;
                String deepResponse;
                if (deepResponsePattern != null) {
                    Map<String, String> freshBindings = new HashMap<>(bindings);
                    refreshDeepenerBinding(freshBindings, deepResponsePattern);
                    deepResponse = DialogueResolver.resolve(deepResponsePattern, freshBindings);
                } else {
                    deepResponse = "I've said all I can about it.";
                }

                nodes.put(deepNodeId, new DialogueNode.DialogueTextNode(
                    deepResponse, List.of(), List.of(), false, false
                ));

                childResponses.add(new ResponseOption(
                    deepResponseId, deepPrompt, null, deepNodeId,
                    ResponseMode.EXPLORATORY, null, null, null, null
                ));
            }

            nodes.put(expNodeId, new DialogueNode.DialogueTextNode(
                response, childResponses, List.of(), false, false
            ));

            parentResponses.add(new ResponseOption(
                responseId, prompt, null, expNodeId,
                ResponseMode.EXPLORATORY, null, null, null, null
            ));

            l1NodeIds.add(expNodeId);
        }

        return l1NodeIds;
    }

    /**
     * Replace deepener pool variable bindings with fresh draws so each
     * L2 branch gets a unique NPC response instead of sharing one pre-resolved value.
     */
    private void refreshDeepenerBinding(Map<String, String> bindings, String pattern) {
        if (pattern.contains("{personal_reaction}")) {
            String bracket = bindings.getOrDefault("_reaction_bracket", "mild");
            bindings.put("personal_reaction", topicPool.randomPersonalReaction(bracket, random));
        }
        if (pattern.contains("{local_opinion}")) {
            String bracket = bindings.getOrDefault("_reaction_bracket", "mild");
            bindings.put("local_opinion", topicPool.randomLocalOpinion(bracket, random));
        }
        if (pattern.contains("{danger_assessment}")) {
            bindings.put("danger_assessment", topicPool.randomDangerAssessment(random));
        }
    }

    /**
     * Inject a skill check as a sibling response on a randomly chosen branch's
     * parent node. Creates SkillCheckNode + pass/fail DialogueTextNodes.
     * The response option uses skillCheckRef to bypass normal node routing.
     */
    private void injectSkillCheck(
            TopicAssignment assignment,
            String subjectId,
            Map<String, String> bindings,
            TopicTemplate.Perspective perspective,
            Map<String, DialogueNode> nodes
    ) {
        TopicTemplate.SkillCheckDef def = assignment.skillCheckDef();
        Skill skill = def.skill();
        Stat stat = skill.getAssociatedStat();
        TopicCategory category = assignment.category();

        // Pick which top-level branch gets the skill check
        int branchCount = perspective.exploratories().size();
        if (branchCount == 0) return;
        int branchIdx = random.nextInt(branchCount);

        // The parent node of the leaf in the chosen branch
        String parentNodeId = subjectId + "_exp_" + branchIdx;
        DialogueNode existing = nodes.get(parentNodeId);
        if (!(existing instanceof DialogueNode.DialogueTextNode parentNode)) return;

        // Roll DC
        int baseDC = STAT_CHECK_DC_MIN + random.nextInt(STAT_CHECK_DC_MAX - STAT_CHECK_DC_MIN + 1);
        baseDC = Math.max(1, baseDC + skill.getDcOffset());

        // Generate text from pools, resolved against bindings
        String promptText = DialogueResolver.resolve(
            topicPool.randomStatCheckPrompt(category, skill, random), bindings);
        String passText = DialogueResolver.resolve(
            topicPool.randomStatCheckPass(category, skill, random), bindings);
        String failText = DialogueResolver.resolve(
            topicPool.randomStatCheckFail(category, skill, random), bindings);

        // Node IDs
        String checkNodeId = subjectId + "_skill_check";
        String passNodeId = subjectId + "_skill_pass";
        String failNodeId = subjectId + "_skill_fail";
        String checkResponseId = subjectId + "_resp_skill_check";

        // Pass node: disposition bonus, exhausts topic
        nodes.put(passNodeId, new DialogueNode.DialogueTextNode(
            passText, List.of(),
            List.of(Map.of("type", "MODIFY_DISPOSITION", "amount",
                String.valueOf(STAT_CHECK_PASS_DISPOSITION))),
            true, false
        ));

        // Fail node: disposition penalty, exhausts topic
        nodes.put(failNodeId, new DialogueNode.DialogueTextNode(
            failText, List.of(),
            List.of(Map.of("type", "MODIFY_DISPOSITION", "amount",
                String.valueOf(STAT_CHECK_FAIL_DISPOSITION))),
            true, false
        ));

        // Skill check node
        nodes.put(checkNodeId, new DialogueNode.SkillCheckNode(
            skill, null, baseDC, true, passNodeId, failNodeId, List.of()
        ));

        // Response option with skillCheckRef + statPrefix
        ResponseOption skillCheckResponse = new ResponseOption(
            checkResponseId,
            skill.displayName(),
            promptText,
            checkNodeId,
            ResponseMode.DECISIVE,
            null,
            checkNodeId,
            stat.name(),
            null
        );

        // Replace parent node: original responses + skill check response
        List<ResponseOption> augmented = new ArrayList<>(parentNode.responses());
        augmented.add(skillCheckResponse);
        nodes.put(parentNodeId, new DialogueNode.DialogueTextNode(
            parentNode.speakerText(), augmented, parentNode.onEnter(),
            parentNode.exhaustsTopic(), parentNode.locksConversation()
        ));
    }

    /**
     * Inject a skill check on a specific L1 node (for intent-based perspectives).
     */
    private void injectSkillCheckOnNode(
            TopicAssignment assignment,
            String subjectId,
            Map<String, String> bindings,
            Map<String, DialogueNode> nodes,
            String parentNodeId
    ) {
        TopicTemplate.SkillCheckDef def = assignment.skillCheckDef();
        Skill skill = def.skill();
        Stat stat = skill.getAssociatedStat();
        TopicCategory category = assignment.category();

        DialogueNode existing = nodes.get(parentNodeId);
        if (!(existing instanceof DialogueNode.DialogueTextNode parentNode)) return;

        int baseDC = STAT_CHECK_DC_MIN + random.nextInt(STAT_CHECK_DC_MAX - STAT_CHECK_DC_MIN + 1);
        baseDC = Math.max(1, baseDC + skill.getDcOffset());

        String promptText = DialogueResolver.resolve(
            topicPool.randomStatCheckPrompt(category, skill, random), bindings);
        String passText = DialogueResolver.resolve(
            topicPool.randomStatCheckPass(category, skill, random), bindings);
        String failText = DialogueResolver.resolve(
            topicPool.randomStatCheckFail(category, skill, random), bindings);

        String checkNodeId = subjectId + "_skill_check";
        String passNodeId = subjectId + "_skill_pass";
        String failNodeId = subjectId + "_skill_fail";
        String checkResponseId = subjectId + "_resp_skill_check";

        nodes.put(passNodeId, new DialogueNode.DialogueTextNode(
            passText, List.of(),
            List.of(Map.of("type", "MODIFY_DISPOSITION", "amount",
                String.valueOf(STAT_CHECK_PASS_DISPOSITION))),
            true, false
        ));

        nodes.put(failNodeId, new DialogueNode.DialogueTextNode(
            failText, List.of(),
            List.of(Map.of("type", "MODIFY_DISPOSITION", "amount",
                String.valueOf(STAT_CHECK_FAIL_DISPOSITION))),
            true, false
        ));

        nodes.put(checkNodeId, new DialogueNode.SkillCheckNode(
            skill, null, baseDC, true, passNodeId, failNodeId, List.of()
        ));

        ResponseOption skillCheckResponse = new ResponseOption(
            checkResponseId,
            skill.displayName(),
            promptText,
            checkNodeId,
            ResponseMode.DECISIVE,
            null,
            checkNodeId,
            stat.name(),
            null
        );

        List<ResponseOption> augmented = new ArrayList<>(parentNode.responses());
        augmented.add(skillCheckResponse);
        nodes.put(parentNodeId, new DialogueNode.DialogueTextNode(
            parentNode.speakerText(), augmented, parentNode.onEnter(),
            parentNode.exhaustsTopic(), parentNode.locksConversation()
        ));
    }

    private static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
