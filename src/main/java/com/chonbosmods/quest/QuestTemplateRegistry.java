package com.chonbosmods.quest;

import com.chonbosmods.quest.model.*;
import com.google.common.flogger.FluentLogger;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class QuestTemplateRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Map<String, QuestSituation> situations = new LinkedHashMap<>();

    public void loadAll(@Nullable Path overrideDir) {
        if (overrideDir != null && Files.isDirectory(overrideDir)) {
            try (Stream<Path> dirs = Files.list(overrideDir)) {
                dirs.filter(Files::isDirectory).forEach(this::loadSituationDir);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to list quest directories in %s", overrideDir);
            }
        }
        LOGGER.atInfo().log("Loaded %d quest situation(s)", situations.size());
    }

    private void loadSituationDir(Path dir) {
        String situationId = dir.getFileName().toString();
        try {
            List<QuestVariant> exposition = loadVariantsFile(dir.resolve("exposition_variants.json"));
            List<QuestVariant> conflict = loadVariantsFile(dir.resolve("conflict_variants.json"));
            List<QuestVariant> resolution = loadVariantsFile(dir.resolve("resolution_variants.json"));
            List<QuestReferenceTemplate> references = loadReferencesFile(dir.resolve("references.json"));
            Map<String, Double> weights = loadWeightsFile(dir.resolve("npc_weights.json"));

            if (exposition.isEmpty() && conflict.isEmpty() && resolution.isEmpty()) {
                LOGGER.atWarning().log("Situation %s has no variants, skipping", situationId);
                return;
            }

            situations.put(situationId, new QuestSituation(
                situationId, exposition, conflict, resolution, references, weights
            ));
            LOGGER.atInfo().log("Loaded situation: %s (%d expo, %d conf, %d reso, %d refs)",
                situationId, exposition.size(), conflict.size(), resolution.size(), references.size());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load situation: %s", situationId);
        }
    }

    private List<QuestVariant> loadVariantsFile(Path file) {
        if (!Files.exists(file)) return List.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray variants = root.getAsJsonArray("variants");
            List<QuestVariant> result = new ArrayList<>();
            for (JsonElement el : variants) {
                result.add(parseVariant(el.getAsJsonObject()));
            }
            return result;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse variants file: %s", file);
            return List.of();
        }
    }

    private QuestVariant parseVariant(JsonObject obj) {
        String id = obj.get("id").getAsString();

        JsonObject chunks = obj.getAsJsonObject("dialogueChunks");
        DialogueChunks dialogueChunks = new DialogueChunks(
            chunks.has("intro") ? chunks.get("intro").getAsString() : "",
            chunks.has("plotStep") ? chunks.get("plotStep").getAsString() : "",
            chunks.has("outro") ? chunks.get("outro").getAsString() : ""
        );

        List<PlayerResponse> responses = new ArrayList<>();
        if (obj.has("playerResponses")) {
            for (JsonElement el : obj.getAsJsonArray("playerResponses")) {
                JsonObject r = el.getAsJsonObject();
                responses.add(new PlayerResponse(
                    r.get("text").getAsString(),
                    r.get("action").getAsString(),
                    r.has("dispositionShift") ? r.get("dispositionShift").getAsInt() : null
                ));
            }
        }

        List<ObjectiveType> pool = new ArrayList<>();
        if (obj.has("objectivePool")) {
            for (JsonElement el : obj.getAsJsonArray("objectivePool")) {
                pool.add(ObjectiveType.valueOf(el.getAsString()));
            }
        }

        Map<ObjectiveType, ObjectiveConfig> configs = new HashMap<>();
        if (obj.has("objectiveConfig")) {
            JsonObject cfgObj = obj.getAsJsonObject("objectiveConfig");
            for (var entry : cfgObj.entrySet()) {
                ObjectiveType type = ObjectiveType.valueOf(entry.getKey());
                JsonObject cfg = entry.getValue().getAsJsonObject();
                configs.put(type, new ObjectiveConfig(
                    cfg.has("countMin") ? cfg.get("countMin").getAsInt() : null,
                    cfg.has("countMax") ? cfg.get("countMax").getAsInt() : null,
                    cfg.has("locationPreference") ? cfg.get("locationPreference").getAsString() : null
                ));
            }
        }

        return new QuestVariant(id, dialogueChunks, responses, pool, configs);
    }

    private List<QuestReferenceTemplate> loadReferencesFile(Path file) {
        if (!Files.exists(file)) return List.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray refs = root.getAsJsonArray("references");
            List<QuestReferenceTemplate> result = new ArrayList<>();
            for (JsonElement el : refs) {
                JsonObject r = el.getAsJsonObject();
                result.add(new QuestReferenceTemplate(
                    r.get("id").getAsString(),
                    jsonArrayToStringList(r.getAsJsonArray("compatibleSituations")),
                    r.get("passiveText").getAsString(),
                    r.get("triggerTopicLabel").getAsString(),
                    r.get("triggerDialogue").getAsString(),
                    jsonArrayToStringList(r.getAsJsonArray("catalystSituations")),
                    jsonArrayToStringList(r.getAsJsonArray("targetNpcRoles"))
                ));
            }
            return result;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse references file: %s", file);
            return List.of();
        }
    }

    private Map<String, Double> loadWeightsFile(Path file) {
        if (!Files.exists(file)) return Map.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Double> weights = new HashMap<>();
            for (var entry : root.entrySet()) {
                weights.put(entry.getKey(), entry.getValue().getAsDouble());
            }
            return weights;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse weights file: %s", file);
            return Map.of();
        }
    }

    private List<String> jsonArrayToStringList(JsonArray arr) {
        List<String> list = new ArrayList<>();
        if (arr != null) {
            for (JsonElement el : arr) list.add(el.getAsString());
        }
        return list;
    }

    public QuestSituation get(String situationId) {
        return situations.get(situationId);
    }

    public Collection<QuestSituation> getAll() {
        return situations.values();
    }

    public QuestSituation selectForRole(String npcRole, Random random) {
        List<QuestSituation> pool = new ArrayList<>(situations.values());
        if (pool.isEmpty()) return null;

        double totalWeight = 0;
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            double w = pool.get(i).getNpcRoleWeights().getOrDefault(npcRole, 0.1);
            weights[i] = w;
            totalWeight += w;
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < pool.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) return pool.get(i);
        }
        return pool.getLast();
    }

    public List<QuestReferenceTemplate> findCompatibleReferences(String situationId) {
        List<QuestReferenceTemplate> result = new ArrayList<>();
        for (QuestSituation sit : situations.values()) {
            for (QuestReferenceTemplate ref : sit.getReferences()) {
                if (ref.compatibleSituations().contains(situationId)) {
                    result.add(ref);
                }
            }
        }
        return result;
    }

    public int getLoadedCount() {
        return situations.size();
    }
}
