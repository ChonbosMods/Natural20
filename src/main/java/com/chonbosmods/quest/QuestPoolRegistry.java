package com.chonbosmods.quest;

import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
                             @Nullable String fetchItemType, int tier) {
        public ItemEntry(String id, String label, String labelPlural) {
            this(id, label, labelPlural, stripArticle(label), stripArticle(labelPlural), null,
                 null, 0, 0, null, 1);
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

    private final List<ItemEntry> collectResources = new ArrayList<>();
    private final List<ItemEntry> keepsakeItems = new ArrayList<>();
    private final List<ItemEntry> evidenceItems = new ArrayList<>();
    private final List<ItemEntry> hostileMobs = new ArrayList<>();

    public void setTemplateRegistry(QuestTemplateRegistry templateRegistry) {
        // No-op retained for call-site compatibility. The template registry was
        // previously required by narrative pools that have since been retired
        // along with the v2 smalltalk-about-quests path. Kept as a stable hook
        // in case post-MVP quest authoring needs to reintroduce a dependency.
    }

    public void loadAll(@Nullable Path poolsDir) {
        // Load from classpath first (bundled resources)
        loadItemPoolFromClasspath(CLASSPATH_PREFIX + "collect_resources.json", "items", collectResources);
        loadItemPoolFromClasspath(CLASSPATH_PREFIX + "evidence_items.json", "values", evidenceItems);
        loadItemPoolFromClasspath(CLASSPATH_PREFIX + "keepsake_items.json", "values", keepsakeItems);
        loadItemPoolFromClasspath(CLASSPATH_PREFIX + "hostile_mobs.json", "mobs", hostileMobs);

        // Override with filesystem if available
        if (poolsDir != null && Files.isDirectory(poolsDir)) {
            loadItemPool(poolsDir.resolve("collect_resources.json"), "items", collectResources);
            loadItemPool(poolsDir.resolve("evidence_items.json"), "values", evidenceItems);
            loadItemPool(poolsDir.resolve("keepsake_items.json"), "values", keepsakeItems);
            loadItemPool(poolsDir.resolve("hostile_mobs.json"), "mobs", hostileMobs);
        }

        LOGGER.atFine().log("Loaded pools: %d resources, %d evidence, %d keepsakes, %d mobs",
            collectResources.size(), evidenceItems.size(), keepsakeItems.size(), hostileMobs.size());
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

            int tier;
            if (obj.has("tier")) {
                JsonElement tierEl = obj.get("tier");
                if (tierEl.isJsonPrimitive() && tierEl.getAsJsonPrimitive().isNumber()) {
                    int raw = tierEl.getAsInt();
                    if (raw < 1 || raw > 4) {
                        LOGGER.atWarning().log("Pool entry %s has tier=%d outside [1,4]; defaulting to 1", id, raw);
                        tier = 1;
                    } else {
                        tier = raw;
                    }
                } else {
                    LOGGER.atWarning().log("Pool entry %s has non-numeric tier; defaulting to 1", id);
                    tier = 1;
                }
            } else {
                // Only COLLECT_RESOURCES entries are expected to have tier. Other pools
                // (mobs, keepsakes, evidence) do not use tier and must not spam warnings.
                if ("items".equals(arrayKey)) {
                    LOGGER.atWarning().log("Collect pool entry %s missing tier; defaulting to 1", id);
                }
                tier = 1;
            }

            target.add(new ItemEntry(id, label, labelPlural, noun, nounPlural, epithet,
                                     category, countMin, countMax, fetchItemType, tier));
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

    public @Nullable ItemEntry findHostileMob(String id) {
        for (ItemEntry entry : hostileMobs) {
            if (entry.id().equals(id)) return entry;
        }
        return null;
    }

    /** Test-only: load the collect pool from an arbitrary JSON file. Not used by production. */
    void loadTestCollectPool(Path file) {
        collectResources.clear();
        loadItemPool(file, "items", collectResources);
    }

    /** Find a collect-pool entry by its Hytale item id. {@code null} if not loaded. */
    public @Nullable ItemEntry findCollectById(String id) {
        for (ItemEntry entry : collectResources) {
            if (entry.id().equals(id)) return entry;
        }
        return null;
    }
}
