package com.chonbosmods.dialogue.model;

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
