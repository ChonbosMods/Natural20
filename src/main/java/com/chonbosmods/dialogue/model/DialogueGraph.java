package com.chonbosmods.dialogue.model;

import com.chonbosmods.quest.DialogueResolver;
import com.google.common.flogger.FluentLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DialogueGraph(
    String npcId,
    int defaultDisposition,
    String greetingNodeId,
    String returnGreetingNodeId,
    List<TopicDefinition> topics,
    Map<String, DialogueNode> nodes
) {
    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    /**
     * Return a shallow copy with mutable topic list and node map, so quest
     * injection can modify the graph without affecting the cached original.
     */
    public DialogueGraph mutableCopy() {
        return new DialogueGraph(
            npcId, defaultDisposition, greetingNodeId, returnGreetingNodeId,
            new ArrayList<>(topics), new LinkedHashMap<>(nodes)
        );
    }

    /**
     * Resolve any remaining {tokens} in all text nodes using the given bindings.
     * Called at conversation start with fresh entity data so variables that were
     * unresolvable at generation time (e.g., no nearby settlements yet) get filled in.
     * Must be called on a mutableCopy() since it replaces node entries.
     */
    public void lateResolve(Map<String, String> bindings) {
        for (var entry : new ArrayList<>(nodes.entrySet())) {
            DialogueNode node = entry.getValue();
            if (node instanceof DialogueNode.DialogueTextNode text) {
                String resolvedSpeaker = DialogueResolver.resolve(text.speakerText(), bindings);
                List<String> resolvedReactions = text.reactionPool() != null
                    ? text.reactionPool().stream().map(r -> DialogueResolver.resolve(r, bindings)).toList()
                    : null;
                List<ResponseOption> resolvedResponses = text.responses().stream()
                    .map(r -> new ResponseOption(
                        r.id(), DialogueResolver.resolve(r.displayText(), bindings),
                        r.logText() != null ? DialogueResolver.resolve(r.logText(), bindings) : null,
                        r.targetNodeId(), r.mode(), r.condition(),
                        r.skillCheckRef(), r.statPrefix(), r.linkedResponses(), r.responseType()))
                    .toList();
                nodes.put(entry.getKey(), new DialogueNode.DialogueTextNode(
                    resolvedSpeaker, resolvedReactions, resolvedResponses,
                    text.onEnter(), text.exhaustsTopic(), text.locksConversation(), text.valence()));
            }
        }
    }

    public DialogueNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public boolean validate() {
        if (greetingNodeId == null || !nodes.containsKey(greetingNodeId)) {
            LOGGER.atSevere().log("Missing greeting node: %s", greetingNodeId);
            return false;
        }
        if (returnGreetingNodeId != null && !nodes.containsKey(returnGreetingNodeId)) {
            LOGGER.atSevere().log("Missing return greeting node: %s", returnGreetingNodeId);
            return false;
        }
        for (TopicDefinition topic : topics) {
            if (!nodes.containsKey(topic.entryNodeId())) {
                LOGGER.atSevere().log("Topic '%s' references missing node: %s", topic.id(), topic.entryNodeId());
                return false;
            }
        }
        return true;
    }
}
