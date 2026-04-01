package com.chonbosmods.topic;

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

    private final List<TopicTemplate> templates = new ArrayList<>();

    public void loadAll(@Nullable Path topicsDir) {
        // Unified templates from classpath
        loadTemplatesFromClasspath("topics/templates.json");

        // Override with filesystem if available
        if (topicsDir != null && Files.isDirectory(topicsDir)) {
            Path unified = topicsDir.resolve("templates.json");
            if (Files.exists(unified)) loadTemplates(unified);
        }

        LOGGER.atFine().log("Loaded %d topic templates", templates.size());
    }

    private void loadTemplatesFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseTemplates(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load templates from classpath: %s", resource);
        }
    }

    private void loadTemplates(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            templates.clear();
            parseTemplates(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load templates: %s", file);
        }
    }

    private void parseTemplates(JsonObject root) {
        JsonArray arr = root.getAsJsonArray("templates");
        if (arr == null) return;
        for (JsonElement el : arr) {
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

            templates.add(new TopicTemplate(
                id, label, subjectRequired, requiresConcrete,
                skills, reactionIntensity, detailPrompts, reactionPrompts, introPattern, decisive
            ));
        }
    }

    public @Nullable TopicTemplate getTemplate(String templateId) {
        return templates.stream()
            .filter(t -> t.id().equals(templateId))
            .findFirst().orElse(null);
    }

    public TopicTemplate randomTemplateForSubject(List<String> subjectCategories,
                                                    boolean subjectConcrete, Random random) {
        List<TopicTemplate> matching = templates.stream()
            .filter(t -> subjectCategories.contains(t.id()))
            .toList();
        if (!subjectConcrete) {
            matching = matching.stream().filter(t -> !t.requiresConcrete()).toList();
        }
        if (matching.isEmpty()) {
            // Fallback: any template, respecting concrete constraint
            List<TopicTemplate> fallback = templates;
            if (!subjectConcrete) {
                fallback = templates.stream().filter(t -> !t.requiresConcrete()).toList();
            }
            if (fallback.isEmpty()) return templates.getFirst();
            return fallback.get(random.nextInt(fallback.size()));
        }
        return matching.get(random.nextInt(matching.size()));
    }
}
