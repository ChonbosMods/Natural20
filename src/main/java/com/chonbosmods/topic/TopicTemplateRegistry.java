package com.chonbosmods.topic;

import com.google.common.flogger.FluentLogger;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TopicTemplateRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final List<TopicTemplate> rumorTemplates = new ArrayList<>();
    private final List<TopicTemplate> smallTalkTemplates = new ArrayList<>();

    public void loadAll(@Nullable Path topicsDir) {
        if (topicsDir == null || !Files.isDirectory(topicsDir)) {
            LOGGER.atWarning().log("Topics directory not found: %s", topicsDir);
            return;
        }

        loadTemplates(topicsDir.resolve("Rumors/templates.json"), TopicCategory.RUMORS, rumorTemplates);
        loadTemplates(topicsDir.resolve("SmallTalk/templates.json"), TopicCategory.SMALLTALK, smallTalkTemplates);

        LOGGER.atInfo().log("Loaded topic templates: %d rumors, %d smalltalk",
            rumorTemplates.size(), smallTalkTemplates.size());
    }

    private void loadTemplates(Path file, TopicCategory category, List<TopicTemplate> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
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

        return new TopicTemplate(id, category, label, perspectives, questHooks);
    }

    private TopicTemplate.Perspective parsePerspective(JsonObject obj) {
        String intro = obj.get("intro").getAsString();

        List<TopicTemplate.FollowUp> exploratories = new ArrayList<>();
        if (obj.has("exploratories")) {
            for (JsonElement el : obj.getAsJsonArray("exploratories")) {
                JsonObject fu = el.getAsJsonObject();
                exploratories.add(new TopicTemplate.FollowUp(
                    fu.get("prompt").getAsString(),
                    fu.get("response").getAsString()
                ));
            }
        }

        TopicTemplate.FollowUp decisive = null;
        if (obj.has("decisive")) {
            JsonObject d = obj.getAsJsonObject("decisive");
            decisive = new TopicTemplate.FollowUp(d.get("prompt").getAsString(), d.get("response").getAsString());
        }

        return new TopicTemplate.Perspective(intro, exploratories, decisive);
    }

    public TopicTemplate randomRumorTemplate(Random random) {
        return rumorTemplates.get(random.nextInt(rumorTemplates.size()));
    }

    public TopicTemplate randomSmallTalkTemplate(Random random) {
        return smallTalkTemplates.get(random.nextInt(smallTalkTemplates.size()));
    }

    public TopicTemplate randomTemplate(TopicCategory category, Random random) {
        return switch (category) {
            case RUMORS -> randomRumorTemplate(random);
            case SMALLTALK -> randomSmallTalkTemplate(random);
        };
    }
}
