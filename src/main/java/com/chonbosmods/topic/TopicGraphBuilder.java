package com.chonbosmods.topic;

import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.quest.DialogueResolver;

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
        String labelTemplate,
        TopicTemplate.Perspective perspective,
        Map<String, String> bindings,
        boolean startVisible,
        boolean hasQuest,
        @Nullable TopicTemplate.SkillCheckDef skillCheckDef,
        TopicCategory category
    ) {}

    private final String npcId;
    private final int defaultDisposition;
    private final String greetingText;
    private final String returnGreetingText;
    private final List<TopicAssignment> assignments;

    public TopicGraphBuilder(
            String npcId,
            int defaultDisposition,
            String greetingText,
            String returnGreetingText,
            List<TopicAssignment> assignments
    ) {
        this.npcId = npcId;
        this.defaultDisposition = defaultDisposition;
        this.greetingText = greetingText;
        this.returnGreetingText = returnGreetingText;
        this.assignments = assignments;
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

        // Exploratory branches
        buildExploratories(perspective.exploratories(), subjectId, "", bindings, nodes, entryResponses);

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
                    responseId, "[Accept] " + prompt, actionNodeId,
                    ResponseMode.DECISIVE, null, null, null, null
                ));
                String declineResponseId = subjectId + "_resp_decline";
                entryResponses.add(new ResponseOption(
                    declineResponseId, "[Decline] Maybe not right now.", declineNodeId,
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
                    responseId, prompt, decisiveNodeId,
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
        String resolvedLabel = DialogueResolver.resolve(assignment.labelTemplate(), bindings);
        resolvedLabel = capitalizeFirst(resolvedLabel);

        DialogueCondition condition = null;
        if (!assignment.startVisible()) {
            condition = new DialogueCondition(
                "TOPIC_LEARNED",
                Map.of("topicId", subjectId),
                null, null
            );
        }

        // Build recap text: last exploratory response, or decisive response, or intro
        String recapText;
        if (decisive != null) {
            recapText = DialogueResolver.resolve(decisive.response(), bindings);
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
                responseId, prompt, expNodeId,
                ResponseMode.EXPLORATORY, null, null, null, null
            ));
        }
    }

    private static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
