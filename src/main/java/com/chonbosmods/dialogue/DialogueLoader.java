package com.chonbosmods.dialogue;

import com.chonbosmods.dialogue.model.*;
import com.chonbosmods.stats.Skill;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class DialogueLoader {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private final Map<String, DialogueGraph> graphsByNpcId = new HashMap<>();

    public void loadAll(@Nullable Path dialogueDir) {
        loadFromClasspath();
        if (dialogueDir != null && Files.isDirectory(dialogueDir)) {
            try {
                loadFromPath(dialogueDir);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to load dialogues from %s", dialogueDir);
            }
        }
        LOGGER.atInfo().log("Loaded %d dialogue graphs", graphsByNpcId.size());
    }

    private void loadFromClasspath() {
        URL dirUrl = getClass().getClassLoader().getResource("dialogues");
        if (dirUrl == null) {
            LOGGER.atWarning().log("No dialogues/ directory found on classpath");
            return;
        }

        try {
            URI dirUri = dirUrl.toURI();
            Path dirPath;
            FileSystem jarFs = null;

            if ("jar".equals(dirUri.getScheme())) {
                jarFs = FileSystems.newFileSystem(dirUri, Map.of());
                dirPath = jarFs.getPath("dialogues");
            } else {
                dirPath = Path.of(dirUri);
            }

            try (Stream<Path> files = Files.list(dirPath)) {
                files.filter(p -> p.toString().endsWith(".json")).forEach(file -> {
                    try (InputStream is = Files.newInputStream(file)) {
                        JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                        DialogueGraph graph = parseGraph(root);
                        if (graph != null && graph.validate()) {
                            graphsByNpcId.put(graph.npcId(), graph);
                        }
                    } catch (IOException e) {
                        LOGGER.atSevere().withCause(e).log("Failed to load classpath dialogue: %s", file);
                    }
                });
            } finally {
                if (jarFs != null) {
                    jarFs.close();
                }
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan classpath dialogues/ directory");
        }
    }

    private void loadFromPath(Path dirPath) throws IOException {
        try (Stream<Path> files = Files.list(dirPath)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(this::loadFile);
        }
    }

    private void loadFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            DialogueGraph graph = parseGraph(root);
            if (graph != null && graph.validate()) {
                graphsByNpcId.put(graph.npcId(), graph);
                LOGGER.atInfo().log("Loaded dialogue: %s from %s", graph.npcId(), file.getFileName());
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse dialogue file: %s", file);
        }
    }

    private DialogueGraph parseGraph(JsonObject root) {
        String npcId = root.get("npcId").getAsString();
        int defaultDisposition = root.has("defaultDisposition") ? root.get("defaultDisposition").getAsInt() : 50;
        String greetingNodeId = root.get("greetingNodeId").getAsString();
        String returnGreetingNodeId = root.has("returnGreetingNodeId")
            ? root.get("returnGreetingNodeId").getAsString() : null;

        List<TopicDefinition> topics = new ArrayList<>();
        if (root.has("topics")) {
            for (JsonElement el : root.getAsJsonArray("topics")) {
                topics.add(parseTopic(el.getAsJsonObject()));
            }
        }

        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        JsonObject nodesObj = root.getAsJsonObject("nodes");
        for (var entry : nodesObj.entrySet()) {
            DialogueNode node = parseNode(entry.getValue().getAsJsonObject());
            if (node != null) {
                nodes.put(entry.getKey(), node);
            }
        }

        DialogueGraph result = new DialogueGraph(npcId, defaultDisposition, greetingNodeId, returnGreetingNodeId, topics, nodes);
        validateLinkedResponses(result);
        return result;
    }

    private TopicDefinition parseTopic(JsonObject obj) {
        return new TopicDefinition(
            obj.get("id").getAsString(),
            obj.get("label").getAsString(),
            obj.get("entryNodeId").getAsString(),
            obj.has("scope") ? TopicScope.valueOf(obj.get("scope").getAsString()) : TopicScope.LOCAL,
            obj.has("condition") ? parseCondition(obj.getAsJsonObject("condition")) : null,
            obj.has("startLearned") ? obj.get("startLearned").getAsBoolean() : true,
            obj.has("statPrefix") ? obj.get("statPrefix").getAsString() : null,
            obj.has("sortOrder") ? obj.get("sortOrder").getAsInt() : 0,
            obj.has("recapText") ? obj.get("recapText").getAsString() : null
        );
    }

    private DialogueNode parseNode(JsonObject obj) {
        String type = obj.get("type").getAsString();
        List<Map<String, String>> onEnter = parseActionList(obj.has("onEnter") ? obj.getAsJsonArray("onEnter") : null);

        return switch (type) {
            case "DIALOGUE" -> parseDialogueNode(obj, onEnter);
            case "SKILL_CHECK" -> parseSkillCheckNode(obj, onEnter);
            case "ACTION" -> parseActionNode(obj, onEnter);
            case "TERMINAL" -> new DialogueNode.TerminalNode(onEnter);
            default -> {
                LOGGER.atWarning().log("Unknown node type: %s", type);
                yield null;
            }
        };
    }

    private DialogueNode.DialogueTextNode parseDialogueNode(JsonObject obj, List<Map<String, String>> onEnter) {
        String speakerText = obj.has("speakerText") ? obj.get("speakerText").getAsString() : "";
        List<ResponseOption> responses = new ArrayList<>();
        if (obj.has("responses")) {
            for (JsonElement el : obj.getAsJsonArray("responses")) {
                responses.add(parseResponse(el.getAsJsonObject()));
            }
        }
        return new DialogueNode.DialogueTextNode(speakerText, responses, onEnter);
    }

    private ResponseOption parseResponse(JsonObject obj) {
        List<String> linkedResponses = null;
        if (obj.has("linkedResponses")) {
            linkedResponses = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("linkedResponses")) {
                linkedResponses.add(el.getAsString());
            }
        }

        return new ResponseOption(
            obj.get("id").getAsString(),
            obj.get("displayText").getAsString(),
            obj.has("targetNodeId") ? obj.get("targetNodeId").getAsString() : null,
            obj.has("mode") ? ResponseMode.valueOf(obj.get("mode").getAsString()) : ResponseMode.DECISIVE,
            obj.has("condition") ? parseCondition(obj.getAsJsonObject("condition")) : null,
            obj.has("skillCheckRef") ? obj.get("skillCheckRef").getAsString() : null,
            obj.has("statPrefix") ? obj.get("statPrefix").getAsString() : null,
            linkedResponses
        );
    }

    private DialogueNode.SkillCheckNode parseSkillCheckNode(JsonObject obj, List<Map<String, String>> onEnter) {
        return new DialogueNode.SkillCheckNode(
            Skill.valueOf(obj.get("skill").getAsString()),
            obj.has("stat") ? Stat.valueOf(obj.get("stat").getAsString()) : null,
            obj.get("baseDC").getAsInt(),
            obj.has("dispositionScaling") && obj.get("dispositionScaling").getAsBoolean(),
            obj.get("passNodeId").getAsString(),
            obj.get("failNodeId").getAsString(),
            onEnter
        );
    }

    private DialogueNode.ActionNode parseActionNode(JsonObject obj, List<Map<String, String>> onEnter) {
        List<Map<String, String>> actions = parseActionList(obj.has("actions") ? obj.getAsJsonArray("actions") : null);
        String next = obj.has("next") ? obj.get("next").getAsString() : null;
        return new DialogueNode.ActionNode(actions, next, onEnter);
    }

    private List<Map<String, String>> parseActionList(@Nullable JsonArray arr) {
        if (arr == null) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject actionObj = el.getAsJsonObject();
            Map<String, String> map = new LinkedHashMap<>();
            for (var entry : actionObj.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
            result.add(map);
        }
        return result;
    }

    private DialogueCondition parseCondition(JsonObject obj) {
        if (obj.has("all")) {
            List<DialogueCondition> children = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("all")) {
                children.add(parseCondition(el.getAsJsonObject()));
            }
            return new DialogueCondition(null, null, children, null);
        }
        if (obj.has("any")) {
            List<DialogueCondition> children = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("any")) {
                children.add(parseCondition(el.getAsJsonObject()));
            }
            return new DialogueCondition(null, null, null, children);
        }

        String type = obj.get("type").getAsString();
        Map<String, String> params = new LinkedHashMap<>();
        for (var entry : obj.entrySet()) {
            if (!"type".equals(entry.getKey())) {
                params.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return new DialogueCondition(type, params, null, null);
    }

    private void validateLinkedResponses(DialogueGraph graph) {
        for (var nodeEntry : graph.nodes().entrySet()) {
            String nodeId = nodeEntry.getKey();
            DialogueNode node = nodeEntry.getValue();
            if (!(node instanceof DialogueNode.DialogueTextNode textNode)) continue;

            Set<String> responseIds = new HashSet<>();
            for (ResponseOption r : textNode.responses()) responseIds.add(r.id());

            Map<String, ResponseOption> responseById = new HashMap<>();
            for (ResponseOption r : textNode.responses()) responseById.put(r.id(), r);

            for (ResponseOption r : textNode.responses()) {
                if (r.linkedResponses() == null || r.linkedResponses().isEmpty()) continue;

                if (r.mode() != ResponseMode.DECISIVE) {
                    LOGGER.atWarning().log("Node '%s': response '%s' has linkedResponses but is %s (must be DECISIVE), ignoring links",
                        nodeId, r.id(), r.mode());
                    continue;
                }

                for (String linkedId : r.linkedResponses()) {
                    if (!responseIds.contains(linkedId)) {
                        LOGGER.atWarning().log("Node '%s': response '%s' links to '%s' which is not on the same node",
                            nodeId, r.id(), linkedId);
                        continue;
                    }

                    ResponseOption linked = responseById.get(linkedId);
                    if (linked != null && linked.mode() != ResponseMode.DECISIVE) {
                        LOGGER.atWarning().log("Node '%s': response '%s' links to '%s' which is %s (must be DECISIVE)",
                            nodeId, r.id(), linkedId, linked.mode());
                    }

                    if (linked != null && (linked.linkedResponses() == null || !linked.linkedResponses().contains(r.id()))) {
                        LOGGER.atWarning().log("Node '%s': response '%s' links to '%s' but '%s' does not link back (should be bidirectional)",
                            nodeId, r.id(), linkedId, linkedId);
                    }
                }
            }
        }
    }

    public DialogueGraph getGraphForNpc(String npcId) {
        return graphsByNpcId.get(npcId);
    }

    public int getLoadedCount() {
        return graphsByNpcId.size();
    }
}
