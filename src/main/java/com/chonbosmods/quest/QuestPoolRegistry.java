package com.chonbosmods.quest;

import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.chonbosmods.quest.model.QuestSituation;
import com.chonbosmods.quest.model.QuestVariant;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class QuestPoolRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_PREFIX = "quests/pools/";

    /** Entry with id + label + optional plural label for items/mobs.
     *  New fields {@code noun}, {@code nounPlural}, {@code epithet} support the
     *  fetch-item authoring model where articles and narrative clauses are removed
     *  from pool data. For pools that don't author these (mobs, collect_resources),
     *  {@code noun}/{@code nounPlural} default to {@code label}/{@code labelPlural}
     *  with leading articles stripped. */
    public record ItemEntry(String id, String label, String labelPlural,
                             String noun, String nounPlural, @Nullable String epithet,
                             String category, int countMin, int countMax,
                             @Nullable String fetchItemType) {
        public ItemEntry(String id, String label, String labelPlural) {
            this(id, label, labelPlural, stripArticle(label), stripArticle(labelPlural), null,
                 null, 0, 0, null);
        }

        /** Strip a leading "a ", "an ", or "the " from a label to produce a bare noun.
         *  Best-effort legacy adapter for unmigrated pools only; not a general-purpose
         *  text utility. Returns the trimmed remainder when an article is matched, or
         *  the input unchanged when no article prefix is found. */
        public static String stripArticle(String s) {
            if (s == null) return null;
            String lower = s.toLowerCase();
            if (lower.startsWith("a ")) return s.substring(2).strip();
            if (lower.startsWith("an ")) return s.substring(3).strip();
            if (lower.startsWith("the ")) return s.substring(4).strip();
            return s;
        }

        /** Compose the full form of this item: {@code noun} followed by {@code epithet}
         *  when an epithet is authored, otherwise just the noun. This is the value
         *  used for the {@code {quest_item_full}} template variable. */
        public String fullForm() {
            return (epithet != null && !epithet.isEmpty()) ? noun + " " + epithet : noun;
        }
    }

    /** Entry with value + plural flag for narrative pools. */
    public record NarrativeEntry(String value, boolean plural, boolean proper) {}

    private final List<ItemEntry> collectResources = new ArrayList<>();
    private final List<ItemEntry> keepsakeItems = new ArrayList<>();
    private final List<ItemEntry> evidenceItems = new ArrayList<>();
    private final List<ItemEntry> hostileMobs = new ArrayList<>();
    private final List<String> questActions = new ArrayList<>();
    private final List<NarrativeEntry> questFocuses = new ArrayList<>();
    private final List<NarrativeEntry> questStakes = new ArrayList<>();
    private final List<NarrativeEntry> questThreats = new ArrayList<>();
    private final List<NarrativeEntry> threatsAnimate = new ArrayList<>();
    private final List<NarrativeEntry> threatsAbstract = new ArrayList<>();
    private final List<NarrativeEntry> stakesHuman = new ArrayList<>();
    private final List<NarrativeEntry> stakesAbstract = new ArrayList<>();
    private final List<String> questOrigins = new ArrayList<>();
    private final List<String> questTimePressures = new ArrayList<>();
    private final List<String> questRewardHints = new ArrayList<>();
    private final Map<String, List<String>> acceptResponses = new HashMap<>();
    private final Map<String, List<String>> declineResponses = new HashMap<>();
    private final Map<String, List<String>> counterAcceptResponses = new HashMap<>();
    private final Map<String, List<String>> counterDeclineResponses = new HashMap<>();
    private final Map<String, List<String>> counterStatPassResponses = new HashMap<>();
    private final Map<String, List<String>> counterStatFailResponses = new HashMap<>();
    private final Map<String, List<String>> statCheckResponses = new HashMap<>();
    private final Map<String, List<String>> targetNpcInfoResponses = new HashMap<>();
    private final Map<String, List<String>> targetNpcHandoffResponses = new HashMap<>();
    private final Map<String, List<String>> sendToNpcResponses = new HashMap<>();
    private final Map<String, List<String>> collectExpoByCategory = new HashMap<>();
    private final Map<String, String> situationTones = new HashMap<>();
    private final Map<String, List<String>> closerPhrases = new HashMap<>();
    private @Nullable QuestTemplateRegistry templateRegistry;

    public void setTemplateRegistry(QuestTemplateRegistry templateRegistry) {
        this.templateRegistry = templateRegistry;
    }

    public void loadAll(@Nullable Path poolsDir) {
        // Load from classpath first (bundled resources)
        loadItemPoolFromClasspath(CLASSPATH_PREFIX + "collect_resources.json", "items", collectResources);
        loadItemPoolFromClasspath(CLASSPATH_PREFIX + "evidence_items.json", "values", evidenceItems);
        loadItemPoolFromClasspath(CLASSPATH_PREFIX + "keepsake_items.json", "values", keepsakeItems);
        loadItemPoolFromClasspath(CLASSPATH_PREFIX + "hostile_mobs.json", "mobs", hostileMobs);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "quest_actions.json", questActions);
        loadNarrativePoolFromClasspath(CLASSPATH_PREFIX + "quest_focuses.json", questFocuses);
        loadNarrativePoolFromClasspath(CLASSPATH_PREFIX + "quest_stakes.json", questStakes);
        loadNarrativePoolFromClasspath(CLASSPATH_PREFIX + "quest_threats.json", questThreats);
        loadNarrativePoolFromClasspath(CLASSPATH_PREFIX + "quest_threats_animate.json", threatsAnimate);
        loadNarrativePoolFromClasspath(CLASSPATH_PREFIX + "quest_threats_abstract.json", threatsAbstract);
        loadNarrativePoolFromClasspath(CLASSPATH_PREFIX + "quest_stakes_human.json", stakesHuman);
        loadNarrativePoolFromClasspath(CLASSPATH_PREFIX + "quest_stakes_abstract.json", stakesAbstract);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "quest_origins.json", questOrigins);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "quest_time_pressures.json", questTimePressures);
        loadStringPoolFromClasspath(CLASSPATH_PREFIX + "quest_reward_hints.json", questRewardHints);
        for (String cat : List.of("farming", "hunting", "mining", "cooking", "woodcraft", "smithing", "textiles", "fishing")) {
            List<String> pool = new ArrayList<>();
            loadStringPoolFromClasspath(CLASSPATH_PREFIX + "collect_expo_" + cat + ".json", pool);
            if (!pool.isEmpty()) collectExpoByCategory.put(cat, pool);
        }

        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "responses_accept.json", acceptResponses);
        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "responses_decline.json", declineResponses);
        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "responses_counter_accept.json", counterAcceptResponses);
        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "responses_counter_decline.json", counterDeclineResponses);
        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "responses_counter_stat_pass.json", counterStatPassResponses);
        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "responses_counter_stat_fail.json", counterStatFailResponses);
        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "responses_stat_check.json", statCheckResponses);
        loadTargetNpcResponsesFromClasspath(CLASSPATH_PREFIX + "responses_target_npc.json");
        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "responses_send_to_npc.json", sendToNpcResponses);
        loadSituationTonesFromClasspath(CLASSPATH_PREFIX + "situation_tones.json");
        loadTonedResponsesFromClasspath(CLASSPATH_PREFIX + "closers.json", closerPhrases);

        // Override with filesystem if available
        if (poolsDir != null && Files.isDirectory(poolsDir)) {
            loadItemPool(poolsDir.resolve("collect_resources.json"), "items", collectResources);
            loadItemPool(poolsDir.resolve("evidence_items.json"), "values", evidenceItems);
            loadItemPool(poolsDir.resolve("keepsake_items.json"), "values", keepsakeItems);
            loadItemPool(poolsDir.resolve("hostile_mobs.json"), "mobs", hostileMobs);
            loadStringPool(poolsDir.resolve("quest_actions.json"), questActions);
            loadNarrativePool(poolsDir.resolve("quest_focuses.json"), questFocuses);
            loadNarrativePool(poolsDir.resolve("quest_stakes.json"), questStakes);
            loadNarrativePool(poolsDir.resolve("quest_threats.json"), questThreats);
            loadNarrativePool(poolsDir.resolve("quest_threats_animate.json"), threatsAnimate);
            loadNarrativePool(poolsDir.resolve("quest_threats_abstract.json"), threatsAbstract);
            loadNarrativePool(poolsDir.resolve("quest_stakes_human.json"), stakesHuman);
            loadNarrativePool(poolsDir.resolve("quest_stakes_abstract.json"), stakesAbstract);
            loadStringPool(poolsDir.resolve("quest_origins.json"), questOrigins);
            loadStringPool(poolsDir.resolve("quest_time_pressures.json"), questTimePressures);
            loadStringPool(poolsDir.resolve("quest_reward_hints.json"), questRewardHints);
            for (String cat : List.of("farming", "hunting", "mining", "cooking", "woodcraft", "smithing", "textiles", "fishing")) {
                List<String> pool = collectExpoByCategory.computeIfAbsent(cat, k -> new ArrayList<>());
                loadStringPool(poolsDir.resolve("collect_expo_" + cat + ".json"), pool);
            }

            loadTonedResponses(poolsDir.resolve("responses_accept.json"), acceptResponses);
            loadTonedResponses(poolsDir.resolve("responses_decline.json"), declineResponses);
            loadTonedResponses(poolsDir.resolve("responses_counter_accept.json"), counterAcceptResponses);
            loadTonedResponses(poolsDir.resolve("responses_counter_decline.json"), counterDeclineResponses);
            loadTonedResponses(poolsDir.resolve("responses_counter_stat_pass.json"), counterStatPassResponses);
            loadTonedResponses(poolsDir.resolve("responses_counter_stat_fail.json"), counterStatFailResponses);
            loadTonedResponses(poolsDir.resolve("responses_stat_check.json"), statCheckResponses);
            loadTargetNpcResponses(poolsDir.resolve("responses_target_npc.json"));
            loadTonedResponses(poolsDir.resolve("responses_send_to_npc.json"), sendToNpcResponses);
            loadSituationTones(poolsDir.resolve("situation_tones.json"));
            loadTonedResponses(poolsDir.resolve("closers.json"), closerPhrases);
        }

        LOGGER.atFine().log("Loaded pools: %d resources, %d evidence, %d keepsakes, %d mobs, %d actions, %d focuses, %d stakes, %d threats, %d origins, %d pressures, %d rewards",
            collectResources.size(), evidenceItems.size(), keepsakeItems.size(), hostileMobs.size(), questActions.size(), questFocuses.size(),
            questStakes.size(), questThreats.size(), questOrigins.size(),
            questTimePressures.size(), questRewardHints.size());
    }

    // --- Classpath loading methods ---

    private void loadItemPoolFromClasspath(String resource, String arrayKey, List<ItemEntry> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseItemEntries(root, arrayKey, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool from classpath: %s", resource);
        }
    }

    private void loadNarrativePoolFromClasspath(String resource, List<NarrativeEntry> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseNarrativeEntries(root, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool from classpath: %s", resource);
        }
    }

    private void loadStringPoolFromClasspath(String resource, List<String> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseStringEntries(root, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool from classpath: %s", resource);
        }
    }

    private void loadTonedResponsesFromClasspath(String resource, Map<String, List<String>> target) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseTonedResponses(root, target);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load responses from classpath: %s", resource);
        }
    }

    private void loadTargetNpcResponsesFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseTargetNpcResponses(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load target NPC responses from classpath: %s", resource);
        }
    }

    private void loadSituationTonesFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            parseSituationTones(root);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load situation tones from classpath: %s", resource);
        }
    }

    // --- Filesystem loading methods ---

    private void loadItemPool(Path file, String arrayKey, List<ItemEntry> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            target.clear();
            parseItemEntries(root, arrayKey, target);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool: %s", file);
        }
    }

    private void loadNarrativePool(Path file, List<NarrativeEntry> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            target.clear();
            parseNarrativeEntries(root, target);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool: %s", file);
        }
    }

    private void loadStringPool(Path file, List<String> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            target.clear();
            parseStringEntries(root, target);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool: %s", file);
        }
    }

    // --- Shared parsing methods ---

    private void parseItemEntries(JsonObject root, String arrayKey, List<ItemEntry> target) {
        JsonArray arr = root.getAsJsonArray(arrayKey);
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            String label = obj.has("label") ? obj.get("label").getAsString() : null;
            String labelPlural = obj.has("labelPlural") ? obj.get("labelPlural").getAsString() : label;

            // New schema: noun/nounPlural/epithet. Fall back to stripped label for
            // pools that haven't been migrated yet.
            String noun = obj.has("noun") ? obj.get("noun").getAsString() : ItemEntry.stripArticle(label);
            String nounPlural = obj.has("nounPlural") ? obj.get("nounPlural").getAsString()
                               : ItemEntry.stripArticle(labelPlural);
            String epithet = obj.has("epithet") && !obj.get("epithet").isJsonNull()
                             ? obj.get("epithet").getAsString() : null;

            // Legacy `label` defaults to the noun if unspecified (so code that still
            // reads .label() sees at least a bare word).
            if (label == null) label = noun;
            if (labelPlural == null) labelPlural = nounPlural;

            String category = obj.has("category") ? obj.get("category").getAsString() : null;
            int countMin = obj.has("countMin") ? obj.get("countMin").getAsInt() : 0;
            int countMax = obj.has("countMax") ? obj.get("countMax").getAsInt() : 0;
            String fetchItemType = obj.has("fetchItemType") ? obj.get("fetchItemType").getAsString() : null;
            target.add(new ItemEntry(id, label, labelPlural, noun, nounPlural, epithet,
                                     category, countMin, countMax, fetchItemType));
        }
    }

    private void parseNarrativeEntries(JsonObject root, List<NarrativeEntry> target) {
        JsonArray arr = root.getAsJsonArray("values");
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                target.add(new NarrativeEntry(
                    obj.get("value").getAsString(),
                    obj.has("plural") && obj.get("plural").getAsBoolean(),
                    obj.has("proper") && obj.get("proper").getAsBoolean()
                ));
            } else {
                target.add(new NarrativeEntry(el.getAsString(), false, false));
            }
        }
    }

    private void parseStringEntries(JsonObject root, List<String> target) {
        JsonArray arr = root.getAsJsonArray("values");
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive()) {
                target.add(el.getAsString());
            } else if (el.isJsonObject()) {
                target.add(el.getAsJsonObject().get("value").getAsString());
            }
        }
    }

    private void parseTonedResponses(JsonObject root, Map<String, List<String>> target) {
        JsonObject tones = root.has("tones") ? root.getAsJsonObject("tones")
            : root.has("stats") ? root.getAsJsonObject("stats") : null;
        if (tones == null) return;
        for (var entry : tones.entrySet()) {
            List<String> responses = new ArrayList<>();
            for (JsonElement el : entry.getValue().getAsJsonArray()) {
                responses.add(el.getAsString());
            }
            target.put(entry.getKey(), responses);
        }
    }

    private void parseTargetNpcResponses(JsonObject root) {
        if (root.has("info")) {
            JsonObject info = root.getAsJsonObject("info");
            for (var entry : info.entrySet()) {
                List<String> responses = new ArrayList<>();
                for (JsonElement el : entry.getValue().getAsJsonArray()) {
                    responses.add(el.getAsString());
                }
                targetNpcInfoResponses.put(entry.getKey(), responses);
            }
        }
        if (root.has("handoff")) {
            JsonObject handoff = root.getAsJsonObject("handoff");
            for (var entry : handoff.entrySet()) {
                List<String> responses = new ArrayList<>();
                for (JsonElement el : entry.getValue().getAsJsonArray()) {
                    responses.add(el.getAsString());
                }
                targetNpcHandoffResponses.put(entry.getKey(), responses);
            }
        }
    }

    private void parseSituationTones(JsonObject root) {
        JsonObject tones = root.getAsJsonObject("tones");
        for (var entry : tones.entrySet()) {
            situationTones.put(entry.getKey(), entry.getValue().getAsString());
        }
    }

    public ItemEntry randomCollectResource(Random random) {
        if (collectResources.isEmpty()) return new ItemEntry("Hytale:Stone", "stone", "stones");
        return collectResources.get(random.nextInt(collectResources.size()));
    }

    public ItemEntry randomKeepsakeItem(Random random) {
        if (keepsakeItems.isEmpty()) return new ItemEntry("keepsake_journal", "a worn leather journal", "worn leather journals");
        return keepsakeItems.get(random.nextInt(keepsakeItems.size()));
    }

    public ItemEntry randomEvidenceItem(Random random) {
        if (evidenceItems.isEmpty()) return new ItemEntry("evidence_ledger", "a signed ledger", "signed ledgers");
        return evidenceItems.get(random.nextInt(evidenceItems.size()));
    }

    public ItemEntry randomHostileMob(Random random) {
        if (hostileMobs.isEmpty()) return new ItemEntry("Hytale:Trork_Grunt", "Trork Grunt", "Trork Grunts");
        return hostileMobs.get(random.nextInt(hostileMobs.size()));
    }

    /**
     * Draw a collect resource exposition line for the given resource category.
     * Falls back to a generic line if no category-specific pool exists.
     */
    public String randomCollectExposition(String category, Random random) {
        List<String> pool = collectExpoByCategory.get(category);
        if (pool != null && !pool.isEmpty()) {
            return pool.get(random.nextInt(pool.size()));
        }
        return "The settlement needs supplies and cannot wait for the usual sources";
    }

    /**
     * Draw a random exposition intro for the given quest situation.
     * Looks up the situation's exposition variants in QuestTemplateRegistry
     * and returns the intro dialogue chunk from a randomly selected variant.
     *
     * @return the intro text, or null if no exposition variants exist for this situation
     */
    public @Nullable String randomExpositionForSituation(String situationId, Random random) {
        if (templateRegistry == null || situationId == null) return null;
        QuestSituation situation = templateRegistry.get(situationId);
        if (situation == null) return null;
        List<QuestVariant> variants = situation.getExpositionVariants();
        if (variants == null || variants.isEmpty()) return null;
        QuestVariant variant = variants.get(random.nextInt(variants.size()));
        String intro = variant.dialogueChunks().intro();
        return (intro != null && !intro.isEmpty()) ? intro : null;
    }

    public String getToneForSituation(String situationId) {
        return situationTones.getOrDefault(situationId, "somber");
    }

    public String randomCounterAccept(String situationId, String tone, Random random) {
        List<String> pool = counterAcceptResponses.get(tone);
        if (pool == null || pool.isEmpty()) return "Thank you.";
        return pool.get(random.nextInt(pool.size()));
    }

    private void loadTargetNpcResponses(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            targetNpcInfoResponses.clear();
            targetNpcHandoffResponses.clear();
            parseTargetNpcResponses(root);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load target NPC responses: %s", file);
        }
    }

    private void loadTonedResponses(Path file, Map<String, List<String>> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            target.clear();
            parseTonedResponses(root, target);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load responses: %s", file);
        }
    }

    /**
     * Resolve the Hytale item type for a quest pool entry.
     * Prefers the per-entry fetchItemType; falls back to category-based defaults.
     */
    public static String getBaseItemType(@Nullable ItemEntry entry) {
        if (entry != null && entry.fetchItemType() != null) {
            return capitalize(entry.fetchItemType());
        }
        return getBaseItemType(entry != null ? entry.id() : null);
    }

    /**
     * Legacy: map a pool item ID to its base Nat20 item type.
     */
    public static String getBaseItemType(@Nullable String poolItemId) {
        if (poolItemId == null) return "Quest_Document";
        if (poolItemId.startsWith("keepsake_")) return "Quest_Keepsake";
        if ("evidence_letter".equals(poolItemId) || "evidence_correspondence".equals(poolItemId))
            return "Quest_Letter";
        if ("evidence_signet".equals(poolItemId) || "evidence_token".equals(poolItemId)
                || "evidence_map".equals(poolItemId))
            return "Quest_Treasure";
        if (poolItemId.startsWith("evidence_")) return "Quest_Document";
        return "Quest_Document";
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (String part : s.split("_")) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append('_');
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private void loadSituationTones(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            situationTones.clear();
            parseSituationTones(root);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load situation tones: %s", file);
        }
    }

    public @Nullable ItemEntry findKeepsakeById(String id) {
        for (ItemEntry entry : keepsakeItems) {
            if (entry.id().equals(id)) return entry;
        }
        return null;
    }

    public @Nullable ItemEntry findEvidenceById(String id) {
        for (ItemEntry entry : evidenceItems) {
            if (entry.id().equals(id)) return entry;
        }
        return null;
    }

    public @Nullable ItemEntry findFetchItemById(String id) {
        ItemEntry entry = findKeepsakeById(id);
        return entry != null ? entry : findEvidenceById(id);
    }
}
