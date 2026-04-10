package com.chonbosmods.quest;

import com.chonbosmods.quest.model.DialogueChunks;
import com.chonbosmods.quest.model.ObjectiveConfig;
import com.chonbosmods.quest.model.QuestReferenceTemplate;
import com.chonbosmods.quest.model.QuestSituation;
import com.chonbosmods.quest.model.QuestTemplateV2;
import com.chonbosmods.quest.model.QuestVariant;
import com.google.common.flogger.FluentLogger;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class QuestTemplateRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_PREFIX = "quests/";

    private final Map<String, QuestSituation> situations = new LinkedHashMap<>();
    private final List<QuestTemplateV2> v2Templates = new ArrayList<>();
    private final List<QuestTemplateV2> mundaneTemplates = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().create();

    public void loadAll(@Nullable Path overrideDir) {
        // Load from classpath first (bundled resources)
        loadFromClasspath();

        // Override with filesystem if available
        if (overrideDir != null && Files.isDirectory(overrideDir)) {
            try (Stream<Path> dirs = Files.list(overrideDir)) {
                dirs.filter(Files::isDirectory).forEach(this::loadSituationDir);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to list quest directories in %s", overrideDir);
            }
        }
        // Load v2 templates
        loadV2Templates(overrideDir);

        LOGGER.atFine().log("Loaded %d quest situation(s)", situations.size());
    }

    private void loadFromClasspath() {
        // Read index.json to discover situation directories
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_PREFIX + "index.json")) {
            if (is == null) {
                LOGGER.atWarning().log("No quests/index.json found on classpath");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray situationNames = root.getAsJsonArray("situations");
            for (JsonElement el : situationNames) {
                String situationId = el.getAsString();
                loadSituationFromClasspath(situationId);
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load quest index from classpath");
        }
    }

    private void loadSituationFromClasspath(String situationId) {
        String prefix = CLASSPATH_PREFIX + situationId + "/";
        try {
            List<QuestVariant> exposition = loadVariantsFromClasspath(prefix + "exposition_variants.json");
            List<QuestVariant> conflict = loadVariantsFromClasspath(prefix + "conflict_variants.json");
            List<QuestVariant> resolution = loadVariantsFromClasspath(prefix + "resolution_variants.json");
            List<QuestReferenceTemplate> references = loadReferencesFromClasspath(prefix + "references.json");
            Map<String, Double> weights = loadWeightsFromClasspath(prefix + "npc_weights.json");

            if (exposition.isEmpty() && conflict.isEmpty() && resolution.isEmpty()) {
                LOGGER.atWarning().log("Situation %s has no variants (classpath), skipping", situationId);
                return;
            }

            situations.put(situationId, new QuestSituation(
                situationId, exposition, conflict, resolution, references, weights
            ));
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load situation from classpath: %s", situationId);
        }
    }

    private List<QuestVariant> loadVariantsFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return List.of();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray variants = root.getAsJsonArray("variants");
            List<QuestVariant> result = new ArrayList<>();
            for (JsonElement el : variants) {
                result.add(parseVariant(el.getAsJsonObject()));
            }
            return result;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse variants from classpath: %s", resource);
            return List.of();
        }
    }

    private List<QuestReferenceTemplate> loadReferencesFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return List.of();
            return parseReferences(JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse references from classpath: %s", resource);
            return List.of();
        }
    }

    private Map<String, Double> loadWeightsFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return Map.of();
            return parseWeights(JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse weights from classpath: %s", resource);
            return Map.of();
        }
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
            LOGGER.atFine().log("Loaded situation: %s (%d expo, %d conf, %d reso, %d refs)",
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

        // Parse optional bindings (exposition variants define these)
        Map<String, String> bindings = new HashMap<>();
        if (obj.has("bindings")) {
            JsonObject bindObj = obj.getAsJsonObject("bindings");
            for (var entry : bindObj.entrySet()) {
                bindings.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        JsonObject chunks = obj.getAsJsonObject("dialogueChunks");
        DialogueChunks dialogueChunks = new DialogueChunks(
            chunks.has("intro") ? chunks.get("intro").getAsString() : "",
            chunks.has("plotStep") ? chunks.get("plotStep").getAsString() : "",
            chunks.has("outro") ? chunks.get("outro").getAsString() : ""
        );

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

        return new QuestVariant(id, bindings, dialogueChunks, pool, configs);
    }

    private List<QuestReferenceTemplate> loadReferencesFile(Path file) {
        if (!Files.exists(file)) return List.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parseReferences(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse references file: %s", file);
            return List.of();
        }
    }

    private List<QuestReferenceTemplate> parseReferences(JsonObject root) {
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
    }

    private Map<String, Double> loadWeightsFile(Path file) {
        if (!Files.exists(file)) return Map.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parseWeights(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse weights file: %s", file);
            return Map.of();
        }
    }

    private Map<String, Double> parseWeights(JsonObject root) {
        Map<String, Double> weights = new HashMap<>();
        for (var entry : root.entrySet()) {
            weights.put(entry.getKey(), entry.getValue().getAsDouble());
        }
        return weights;
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

    /**
     * Look up a specific variant by situation, phase type, and variant ID.
     */
    public @Nullable QuestVariant getVariant(String situationId, PhaseType phaseType, String variantId) {
        QuestSituation situation = situations.get(situationId);
        if (situation == null) return null;

        List<QuestVariant> pool = switch (phaseType) {
            case EXPOSITION -> situation.getExpositionVariants();
            case CONFLICT -> situation.getConflictVariants();
            case RESOLUTION -> situation.getResolutionVariants();
        };

        for (QuestVariant v : pool) {
            if (variantId.equals(v.id())) return v;
        }
        return null;
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

    private void loadV2Templates(@Nullable Path questDataDir) {
        loadV2FromClasspath();
        if (questDataDir != null) {
            Path v2Index = questDataDir.resolve("v2").resolve("index.json");
            if (Files.isRegularFile(v2Index)) {
                loadV2FromFile(v2Index);
            }
        }
        LOGGER.atInfo().log("Loaded %d v2 quest template(s)", v2Templates.size());
        loadMundaneTemplates();
    }

    /**
     * Load v2 templates from a single combined catalog at {@code quests/v2/index.json}.
     * The file format is {@code { "templates": [ <inline template object>, ... ] }}.
     * Each entry must match the {@link QuestTemplateV2} record shape.
     */
    private void loadV2FromClasspath() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("quests/v2/index.json")) {
            if (is == null) {
                LOGGER.atWarning().log("No quests/v2/index.json found on classpath");
                return;
            }
            parseV2Catalog(JsonParser.parseReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject(), "classpath");
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load v2 templates from classpath");
        }
    }

    private void loadV2FromFile(Path path) {
        try (var reader = Files.newBufferedReader(path)) {
            parseV2Catalog(JsonParser.parseReader(reader).getAsJsonObject(), path.toString());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load v2 template catalog from %s", path);
        }
    }

    private void loadMundaneTemplates() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("quests/mundane/index.json")) {
            if (is == null) {
                LOGGER.atInfo().log("No quests/mundane/index.json found on classpath, mundane quests disabled");
                return;
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray templates = root.getAsJsonArray("templates");
            if (templates == null) {
                LOGGER.atWarning().log("Mundane catalog has no 'templates' array");
                return;
            }
            Set<String> seenIds = new HashSet<>();
            int loaded = 0, skipped = 0;
            for (JsonElement el : templates) {
                QuestTemplateV2 template;
                try {
                    template = GSON.fromJson(el, QuestTemplateV2.class);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to parse mundane template entry");
                    skipped++;
                    continue;
                }
                if (template == null || template.id() == null || template.id().isEmpty()) {
                    LOGGER.atWarning().log("Mundane template missing 'id', skipping");
                    skipped++;
                    continue;
                }
                if (template.topicHeader() == null || template.topicHeader().isEmpty()) {
                    LOGGER.atWarning().log("Mundane template '%s' missing 'topicHeader', skipping", template.id());
                    skipped++;
                    continue;
                }
                if (template.objectives() == null || template.objectives().isEmpty()) {
                    LOGGER.atWarning().log("Mundane template '%s' has no objectives, skipping", template.id());
                    skipped++;
                    continue;
                }
                if (!seenIds.add(template.id())) {
                    LOGGER.atWarning().log("Mundane template id '%s' duplicated, skipping", template.id());
                    skipped++;
                    continue;
                }
                mundaneTemplates.add(template);
                loaded++;
            }
            LOGGER.atInfo().log("Loaded %d mundane quest template(s) (%d skipped)", loaded, skipped);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load mundane templates from classpath");
        }
    }

    private void parseV2Catalog(JsonObject root, String source) {
        JsonArray templates = root.getAsJsonArray("templates");
        if (templates == null) {
            LOGGER.atWarning().log("v2 catalog at %s has no 'templates' array", source);
            return;
        }
        Set<String> seenIds = new HashSet<>();
        for (QuestTemplateV2 existing : v2Templates) {
            if (existing.id() != null) seenIds.add(existing.id());
        }
        int loaded = 0, skipped = 0;
        for (JsonElement el : templates) {
            QuestTemplateV2 template;
            try {
                template = GSON.fromJson(el, QuestTemplateV2.class);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to parse v2 template entry from %s", source);
                skipped++;
                continue;
            }
            if (template == null || template.id() == null || template.id().isEmpty()) {
                LOGGER.atWarning().log("v2 template missing 'id' from %s, skipping", source);
                skipped++;
                continue;
            }
            if (template.topicHeader() == null || template.topicHeader().isEmpty()) {
                LOGGER.atWarning().log("v2 template '%s' missing 'topicHeader', skipping", template.id());
                skipped++;
                continue;
            }
            if (template.objectives() == null || template.objectives().isEmpty()) {
                LOGGER.atWarning().log("v2 template '%s' has no objectives, skipping", template.id());
                skipped++;
                continue;
            }
            long talkCount = template.objectives().stream()
                    .filter(o -> "TALK_TO_NPC".equals(o.type()))
                    .count();
            if (talkCount > 2) {
                LOGGER.atWarning().log("v2 template '%s' has %d TALK_TO_NPC objectives (max 2), skipping",
                        template.id(), talkCount);
                skipped++;
                continue;
            }
            if (!seenIds.add(template.id())) {
                LOGGER.atWarning().log("v2 template id '%s' duplicated in %s, skipping", template.id(), source);
                skipped++;
                continue;
            }
            v2Templates.add(template);
            loaded++;
        }
        LOGGER.atFine().log("Loaded %d v2 templates from %s (%d skipped)", loaded, source, skipped);
    }

    public List<QuestTemplateV2> getV2Templates() { return v2Templates; }

    /** Probability [0.0, 1.0] that a quest rolls mundane instead of dramatic. */
    private static final double MUNDANE_WEIGHT = 0.25;

    /**
     * Select a v2 template eligible for the given NPC role. First rolls whether
     * the quest should be mundane (default 25%) or dramatic. Within the chosen
     * pool, eligibility is a hard filter on {@link QuestTemplateV2#roleAffinity()}.
     */
    public @Nullable QuestTemplateV2 selectV2ForRole(String npcRole, Random random) {
        if (!mundaneTemplates.isEmpty() && random.nextDouble() < MUNDANE_WEIGHT) {
            QuestTemplateV2 mundane = selectFromPool(mundaneTemplates, npcRole, random);
            if (mundane != null) return mundane;
        }
        return selectFromPool(v2Templates, npcRole, random);
    }

    private @Nullable QuestTemplateV2 selectFromPool(List<QuestTemplateV2> pool, String npcRole, Random random) {
        List<QuestTemplateV2> eligible = new ArrayList<>();
        for (QuestTemplateV2 t : pool) {
            List<String> aff = t.roleAffinity();
            if (aff == null || aff.isEmpty() || aff.contains(npcRole)) {
                eligible.add(t);
            }
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(random.nextInt(eligible.size()));
    }

    public List<QuestTemplateV2> getMundaneTemplates() { return mundaneTemplates; }
}
