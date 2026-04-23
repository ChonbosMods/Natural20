package com.chonbosmods.quest;

import com.chonbosmods.quest.model.QuestTemplateV2;
import com.chonbosmods.quest.model.QuestVariant;
import com.chonbosmods.quest.model.SkillCheckAdapter;
import com.google.common.flogger.FluentLogger;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class QuestTemplateRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final List<QuestTemplateV2> v2Templates = new ArrayList<>();
    private final List<QuestTemplateV2> mundaneTemplates = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(QuestTemplateV2.SkillCheck.class, new SkillCheckAdapter())
        .create();

    public void loadAll(@Nullable Path overrideDir) {
        loadV2Templates(overrideDir);
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
