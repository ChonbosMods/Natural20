package com.chonbosmods.topic;

import com.chonbosmods.stats.Skill;
import com.google.common.flogger.FluentLogger;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TopicTemplateRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private static final TopicTemplate FALLBACK_TEMPLATE = TopicTemplate.ofLegacy(
        "fallback", TopicCategory.RUMORS, "{subject_focus}", true, false,
        List.of(new TopicTemplate.Perspective("I've heard about the {subject_focus}.",
            List.of(new TopicTemplate.FollowUp("Tell me more.", "That's all I know, really.", List.of())), null, null, null)),
        List.of(),
        null
    );

    private final List<TopicTemplate> rumorTemplates = new ArrayList<>();
    private final List<TopicTemplate> smallTalkTemplates = new ArrayList<>();
    private final List<TopicTemplate> unifiedTemplates = new ArrayList<>();

    public void loadAll(@Nullable Path topicsDir) {
        // Load from classpath first (bundled resources)
        loadTemplatesFromClasspath("topics/Rumors/templates.json", TopicCategory.RUMORS, rumorTemplates);
        loadTemplatesFromClasspath("topics/SmallTalk/templates.json", TopicCategory.SMALLTALK, smallTalkTemplates);

        // Override with filesystem if available
        if (topicsDir != null && Files.isDirectory(topicsDir)) {
            loadTemplates(topicsDir.resolve("Rumors/templates.json"), TopicCategory.RUMORS, rumorTemplates);
            loadTemplates(topicsDir.resolve("SmallTalk/templates.json"), TopicCategory.SMALLTALK, smallTalkTemplates);
        }

        // Unified v2 templates
        loadUnifiedTemplatesFromClasspath("topics/templates.json");
        if (topicsDir != null && Files.isDirectory(topicsDir)) {
            Path unified = topicsDir.resolve("templates.json");
            if (Files.exists(unified)) loadUnifiedTemplates(unified);
        }

        LOGGER.atFine().log("Loaded topic templates: %d rumors, %d smalltalk, %d unified",
            rumorTemplates.size(), smallTalkTemplates.size(), unifiedTemplates.size());
    }

    private void loadTemplatesFromClasspath(String resource, TopicCategory category, List<TopicTemplate> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray templates = root.getAsJsonArray("templates");
            for (JsonElement el : templates) {
                target.add(parseTemplate(el.getAsJsonObject(), category));
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load templates from classpath: %s", resource);
        }
    }

    private void loadTemplates(Path file, TopicCategory category, List<TopicTemplate> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            target.clear();
            JsonArray templates = root.getAsJsonArray("templates");
            for (JsonElement el : templates) {
                target.add(parseTemplate(el.getAsJsonObject(), category));
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load templates: %s", file);
        }
    }

    private TopicTemplate parseTemplate(JsonObject obj, TopicCategory category) {
        String id = obj.get("id").getAsString();
        String label = obj.has("label") ? obj.get("label").getAsString() : "{subject_focus}";

        List<TopicTemplate.Perspective> perspectives = new ArrayList<>();
        if (obj.has("perspectives")) {
            for (JsonElement el : obj.getAsJsonArray("perspectives")) {
                perspectives.add(parsePerspective(el.getAsJsonObject()));
            }
        }

        List<TopicTemplate.Perspective> questHooks = new ArrayList<>();
        if (obj.has("questHookPerspectives")) {
            for (JsonElement el : obj.getAsJsonArray("questHookPerspectives")) {
                questHooks.add(parsePerspective(el.getAsJsonObject()));
            }
        }

        TopicTemplate.SkillCheckDef skillCheckDef = null;
        if (obj.has("skillCheck")) {
            JsonObject sc = obj.getAsJsonObject("skillCheck");
            Skill skill = Skill.valueOf(sc.get("skill").getAsString());
            skillCheckDef = new TopicTemplate.SkillCheckDef(skill);
        }

        boolean subjectRequired = !obj.has("subjectRequired") || obj.get("subjectRequired").getAsBoolean();
        boolean requiresConcrete = obj.has("requiresConcrete") && obj.get("requiresConcrete").getAsBoolean();

        return TopicTemplate.ofLegacy(id, category, label, subjectRequired, requiresConcrete, perspectives, questHooks, skillCheckDef);
    }

    private TopicTemplate.Perspective parsePerspective(JsonObject obj) {
        String intro = obj.get("intro").getAsString();

        // Old format: "exploratories" array of nested follow-ups
        List<TopicTemplate.FollowUp> exploratories = new ArrayList<>();
        if (obj.has("exploratories")) {
            for (JsonElement el : obj.getAsJsonArray("exploratories")) {
                exploratories.add(parseFollowUp(el.getAsJsonObject()));
            }
        }

        // New format: "intents" array of intent slots
        List<TopicTemplate.IntentSlot> intents = null;
        String deepenerResponse = null;
        if (obj.has("intents")) {
            intents = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("intents")) {
                intents.add(parseIntentSlot(el.getAsJsonObject()));
            }
            if (obj.has("deepenerResponse")) {
                deepenerResponse = obj.get("deepenerResponse").getAsString();
            }
        }

        TopicTemplate.FollowUp decisive = null;
        if (obj.has("decisive")) {
            decisive = parseFollowUp(obj.getAsJsonObject("decisive"));
        }

        return new TopicTemplate.Perspective(intro, exploratories, intents, deepenerResponse, decisive);
    }

    private TopicTemplate.IntentSlot parseIntentSlot(JsonObject obj) {
        String intent = obj.get("intent").getAsString();
        String response = obj.get("response").getAsString();
        String deepenerResponse = obj.has("deepenerResponse") ? obj.get("deepenerResponse").getAsString() : null;
        return new TopicTemplate.IntentSlot(intent, response, deepenerResponse);
    }

    private TopicTemplate.FollowUp parseFollowUp(JsonObject obj) {
        String prompt = obj.get("prompt").getAsString();
        String response = obj.get("response").getAsString();

        List<TopicTemplate.FollowUp> children = new ArrayList<>();
        if (obj.has("exploratories")) {
            for (JsonElement el : obj.getAsJsonArray("exploratories")) {
                children.add(parseFollowUp(el.getAsJsonObject()));
            }
        }

        return new TopicTemplate.FollowUp(prompt, response, children);
    }

    public TopicTemplate randomRumorTemplate(Random random) {
        if (rumorTemplates.isEmpty()) return FALLBACK_TEMPLATE;
        return rumorTemplates.get(random.nextInt(rumorTemplates.size()));
    }

    public TopicTemplate randomSmallTalkTemplate(Random random) {
        if (smallTalkTemplates.isEmpty()) return FALLBACK_TEMPLATE;
        return smallTalkTemplates.get(random.nextInt(smallTalkTemplates.size()));
    }

    public TopicTemplate randomTemplate(TopicCategory category, Random random) {
        return switch (category) {
            case RUMORS -> randomRumorTemplate(random);
            case SMALLTALK -> randomSmallTalkTemplate(random);
        };
    }

    /**
     * Select a template whose topic matches one of the subject's categories.
     * Searches across all template lists (rumors, smalltalk, unified).
     * Falls back to unfiltered random if no matching template is found.
     */
    public TopicTemplate randomTemplateForSubject(List<String> subjectCategories,
                                                    boolean subjectConcrete, Random random) {
        // Build a combined pool from all template sources
        List<TopicTemplate> allTemplates = new ArrayList<>(rumorTemplates.size() + smallTalkTemplates.size() + unifiedTemplates.size());
        allTemplates.addAll(rumorTemplates);
        allTemplates.addAll(smallTalkTemplates);
        allTemplates.addAll(unifiedTemplates);

        if (subjectCategories.isEmpty()) {
            if (allTemplates.isEmpty()) return FALLBACK_TEMPLATE;
            return allTemplates.get(random.nextInt(allTemplates.size()));
        }

        List<TopicTemplate> matching = allTemplates.stream()
            .filter(t -> {
                String topic = t.id().contains("_") ? t.id().substring(t.id().indexOf('_') + 1) : t.id();
                return subjectCategories.contains(topic) || subjectCategories.contains(t.id());
            })
            .toList();
        if (!subjectConcrete) {
            matching = matching.stream()
                .filter(t -> !t.requiresConcrete())
                .toList();
        }
        if (matching.isEmpty()) {
            // Fallback: pick any template, still respecting concrete constraint
            List<TopicTemplate> fallback = allTemplates;
            if (!subjectConcrete) {
                fallback = allTemplates.stream().filter(t -> !t.requiresConcrete()).toList();
            }
            if (fallback.isEmpty()) return FALLBACK_TEMPLATE;
            return fallback.get(random.nextInt(fallback.size()));
        }
        return matching.get(random.nextInt(matching.size()));
    }

    // --- Unified v2 template loading ---

    private void loadUnifiedTemplatesFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseUnifiedTemplates(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load unified templates from classpath: %s", resource);
        }
    }

    private void loadUnifiedTemplates(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            unifiedTemplates.clear();
            parseUnifiedTemplates(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load unified templates: %s", file);
        }
    }

    private void parseUnifiedTemplates(JsonObject root) {
        JsonArray templates = root.getAsJsonArray("templates");
        if (templates == null) return;
        for (JsonElement el : templates) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            String label = obj.has("label") ? obj.get("label").getAsString() : "{subject_focus}";
            boolean subjectRequired = !obj.has("subjectRequired") || obj.get("subjectRequired").getAsBoolean();
            boolean requiresConcrete = obj.has("requiresConcrete") && obj.get("requiresConcrete").getAsBoolean();

            List<String> skills = new ArrayList<>();
            if (obj.has("skills")) {
                for (JsonElement s : obj.getAsJsonArray("skills")) skills.add(s.getAsString());
            }

            String reactionIntensity = obj.has("reactionIntensity") ? obj.get("reactionIntensity").getAsString() : null;

            String introPattern = obj.has("intro") ? obj.get("intro").getAsString() : null;

            List<String> detailPrompts = new ArrayList<>();
            if (obj.has("detailPrompts")) {
                for (JsonElement d : obj.getAsJsonArray("detailPrompts")) detailPrompts.add(d.getAsString());
            }

            List<String> reactionPrompts = new ArrayList<>();
            if (obj.has("reactionPrompts")) {
                for (JsonElement r : obj.getAsJsonArray("reactionPrompts")) reactionPrompts.add(r.getAsString());
            }

            TopicTemplate.Decisive decisive = null;
            if (obj.has("decisive")) {
                JsonObject d = obj.getAsJsonObject("decisive");
                decisive = new TopicTemplate.Decisive(d.get("prompt").getAsString(), d.get("response").getAsString());
            }

            unifiedTemplates.add(new TopicTemplate(
                id, null, label, subjectRequired, requiresConcrete,
                List.of(), List.of(), null,
                skills, reactionIntensity, detailPrompts, reactionPrompts, introPattern, decisive
            ));
        }
    }

    public @Nullable TopicTemplate getUnifiedTemplate(String templateId) {
        return unifiedTemplates.stream()
            .filter(t -> t.id().equals(templateId))
            .findFirst().orElse(null);
    }

    public TopicTemplate randomUnifiedTemplateForSubject(List<String> subjectCategories,
                                                          boolean subjectConcrete, Random random) {
        List<TopicTemplate> matching = unifiedTemplates.stream()
            .filter(t -> subjectCategories.contains(t.id()))
            .toList();
        if (!subjectConcrete) {
            matching = matching.stream().filter(t -> !t.requiresConcrete()).toList();
        }
        if (matching.isEmpty()) {
            // Fallback: any template, respecting concrete constraint
            List<TopicTemplate> fallback = unifiedTemplates;
            if (!subjectConcrete) {
                fallback = unifiedTemplates.stream().filter(t -> !t.requiresConcrete()).toList();
            }
            if (fallback.isEmpty()) return unifiedTemplates.getFirst();
            return fallback.get(random.nextInt(fallback.size()));
        }
        return matching.get(random.nextInt(matching.size()));
    }
}
