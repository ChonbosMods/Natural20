package com.chonbosmods.topic;

import com.chonbosmods.dialogue.DifficultyTier;
import com.chonbosmods.dialogue.ValenceType;
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
        @Nullable Skill skill,
        @Nullable TopicTemplate template,
        @Nullable PoolEntry entry
    ) {}

    private final String npcId;
    private final int defaultDisposition;
    private final String greetingText;
    private final String returnGreetingText;
    private final List<TopicAssignment> assignments;
    private final TopicPoolRegistry topicPool;
    private final Random random;
    public TopicGraphBuilder(
            String npcId,
            int defaultDisposition,
            String greetingText,
            String returnGreetingText,
            List<TopicAssignment> assignments,
            TopicPoolRegistry topicPool,
            Random random
    ) {
        this.npcId = npcId;
        this.defaultDisposition = defaultDisposition;
        this.greetingText = greetingText;
        this.returnGreetingText = returnGreetingText;
        this.assignments = assignments;
        this.topicPool = topicPool;
        this.random = random;
    }

    public DialogueGraph build() {
        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        List<TopicDefinition> topics = new ArrayList<>();

        // Greeting nodes: plain speech with no responses
        String greetingNodeId = "greeting";
        String returnGreetingNodeId = "return_greeting";

        nodes.put(greetingNodeId, new DialogueNode.DialogueTextNode(
            greetingText, null, List.of(), List.of(), false, false, null
        ));
        nodes.put(returnGreetingNodeId, new DialogueNode.DialogueTextNode(
            returnGreetingText, null, List.of(), List.of(), false, false, null
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
        ValenceType entryValence = entry.valence();

        String entryNodeId = subjectId + "_entry";

        // Resolve intro
        String introPattern = template.introPattern() != null ? template.introPattern() : "{entry_intro}";
        String introText = DialogueResolver.resolve(introPattern, bindings);

        List<ResponseOption> entryResponses = new ArrayList<>();

        // --- Simplified mundane flow: variable-length CONTINUE chain ---

        // 1. Collect ALL detail and reaction lines
        List<String> allBeats = new ArrayList<>();
        for (String detail : entry.details()) {
            allBeats.add(DialogueResolver.resolve(detail, bindings));
        }
        for (String reaction : entry.reactions()) {
            allBeats.add(DialogueResolver.resolve(reaction, bindings));
        }

        // 2. Shuffle with deterministic seed
        long shuffleSeed = Objects.hash(npcId, entry.id(), "shuffle");
        Collections.shuffle(allBeats, new Random(shuffleSeed));

        // 3. Select how many beats to show (deterministic per NPC/entry)
        Random beatCountRng = new Random(Objects.hash(npcId, entry.id(), "beatcount"));
        double beatRoll = beatCountRng.nextDouble();
        int beatsToShow;
        if (beatRoll < MundaneDispositionConstants.BEAT_COUNT_1_CHANCE) {
            beatsToShow = 1;
        } else if (beatRoll < MundaneDispositionConstants.BEAT_COUNT_1_CHANCE
                + MundaneDispositionConstants.BEAT_COUNT_2_CHANCE) {
            beatsToShow = 2;
        } else {
            beatsToShow = 3;
        }
        beatsToShow = Math.min(beatsToShow, allBeats.size());

        List<String> remainingBeats = new ArrayList<>(allBeats.subList(0, beatsToShow));

        // 4. Determine stat check placement (uniform random among displayed beats)
        boolean statCheckApproved = entry.statCheck() != null
                && assignment.skill() != null
                && random.nextDouble() < MundaneDispositionConstants.STAT_CHECK_INCLUSION_CHANCE;

        int statCheckBeat = -1; // -1 means no stat check
        if (statCheckApproved) {
            int totalDisplayedBeats = 1 + remainingBeats.size(); // intro + selected beats
            statCheckBeat = new Random(Objects.hash(npcId, entry.id(), "statcheck"))
                    .nextInt(totalDisplayedBeats);
        }

        // 5. Build stat check side-branch nodes (if approved)
        String checkNodeId = null;
        if (statCheckApproved) {
            Skill skill = assignment.skill();

            checkNodeId = subjectId + "_skill_check";
            String passNodeId = subjectId + "_skill_pass";
            String failNodeId = subjectId + "_skill_fail";

            String passText = DialogueResolver.resolve(entry.statCheck().pass(), bindings);
            String failText = DialogueResolver.resolve(entry.statCheck().fail(), bindings);

            // Pass/fail nodes: empty mutable response lists.
            // ConversationSession will inject CONTINUE responses at runtime
            // pointing to the next beat in the chain.
            nodes.put(passNodeId, new DialogueNode.DialogueTextNode(
                passText, null, new ArrayList<>(),
                List.of(Map.of("type", "MODIFY_DISPOSITION", "amount",
                    String.valueOf(MundaneDispositionConstants.STAT_CHECK_PASS))),
                false, false, entryValence
            ));
            nodes.put(failNodeId, new DialogueNode.DialogueTextNode(
                failText, null, new ArrayList<>(),
                List.of(Map.of("type", "MODIFY_DISPOSITION", "amount",
                    String.valueOf(MundaneDispositionConstants.STAT_CHECK_FAIL))),
                false, false, entryValence
            ));
            nodes.put(checkNodeId, new DialogueNode.SkillCheckNode(
                skill, null, DifficultyTier.MEDIUM, true, passNodeId, failNodeId, List.of()
            ));
        }

        // 6. Build the linear chain: intro -> shuffled[0] -> shuffled[1] -> ...
        List<String> chainNodeIds = new ArrayList<>();
        chainNodeIds.add(entryNodeId);

        for (int i = 0; i < remainingBeats.size(); i++) {
            String beatNodeId = subjectId + "_beat_" + i;
            chainNodeIds.add(beatNodeId);
            nodes.put(beatNodeId, new DialogueNode.DialogueTextNode(
                remainingBeats.get(i), null, new ArrayList<>(),
                List.of(), false, false, entryValence
            ));
        }

        // 7. Wire CONTINUE responses and stat check responses
        for (int i = 0; i < chainNodeIds.size(); i++) {
            boolean isLastBeat = (i == chainNodeIds.size() - 1);
            List<ResponseOption> responses;

            if (i == 0) {
                // Entry node: entryResponses (created earlier, currently empty)
                responses = entryResponses;
            } else {
                // Beat node: get the mutable response list
                DialogueNode.DialogueTextNode beatNode =
                    (DialogueNode.DialogueTextNode) nodes.get(chainNodeIds.get(i));
                responses = (List<ResponseOption>) beatNode.responses();
            }

            // CONTINUE to next beat (not on last beat)
            if (!isLastBeat) {
                String nextNodeId = chainNodeIds.get(i + 1);
                responses.add(new ResponseOption(
                    subjectId + "_continue_" + i, "CONTINUE", null, nextNodeId,
                    ResponseMode.DECISIVE, null, null, null, null,
                    ResponseType.CONTINUE
                ));
            }

            // Stat check response (on exactly one beat)
            if (statCheckApproved && i == statCheckBeat) {
                Skill skill = assignment.skill();
                Stat stat = skill.getStat();
                responses.add(new ResponseOption(
                    subjectId + "_resp_skill_check",
                    skill.displayName(),
                    null,
                    checkNodeId,
                    ResponseMode.DECISIVE,
                    null,
                    checkNodeId,
                    stat.name(),
                    null
                ));
            }
        }

        // Entry node: UNLOCK_TOPIC on enter so the topic is globally available
        List<Map<String, String>> entryOnEnter =
            List.of(Map.of("type", "UNLOCK_TOPIC", "topicId", subjectId, "scope", "GLOBAL"));
        nodes.put(entryNodeId, new DialogueNode.DialogueTextNode(
            introText, null, entryResponses, entryOnEnter, false, false, entryValence
        ));

        // Topic definition
        String resolvedLabel = capitalizeFirst(DialogueResolver.resolve(assignment.labelPattern(), bindings));
        String recapText = (!entry.reactions().isEmpty())
            ? DialogueResolver.resolve(entry.reactions().getFirst(), bindings)
            : introText;

        topics.add(new TopicDefinition(
            subjectId, resolvedLabel, entryNodeId,
            TopicScope.GLOBAL, null, assignment.startVisible(),
            null, sortOrder, recapText, false
        ));
    }

    private static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
