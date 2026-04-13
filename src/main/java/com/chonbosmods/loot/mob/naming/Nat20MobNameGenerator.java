package com.chonbosmods.loot.mob.naming;

import com.chonbosmods.loot.mob.EncounterTier;
import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates Diablo 2-style compound names for elite mobs.
 * Loads word pools from {@code /loot/mob_names/elite_name_pools.json} on the classpath,
 * then combines a prefix + suffix (and optionally a title appellation) based on the
 * mob's encounter tier.
 */
public class Nat20MobNameGenerator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private static final String POOL_RESOURCE = "/loot/mob_names/elite_name_pools.json";
    private static final int MAX_RETRY = 10;

    private final List<MobNameWord> prefixes = new ArrayList<>();
    private final List<MobNameWord> suffixes = new ArrayList<>();
    private final List<MobNameAppellation> appellations = new ArrayList<>();

    private int dedupWindow = 50;
    private double rareTitleChance = 0.5;

    private final Deque<String> recentNames = new ArrayDeque<>();

    // ── Loading ──────────────────────────────────────────────────────────

    /**
     * Load word pools from the bundled JSON resource.
     * Call once during system initialization (e.g., from Nat20LootSystem.loadAll()).
     */
    public void load() {
        try (InputStream is = getClass().getResourceAsStream(POOL_RESOURCE)) {
            if (is == null) {
                LOGGER.atSevere().log("Elite name pool resource not found: %s", POOL_RESOURCE);
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            loadConfig(root);
            loadWords(root, "prefixes", prefixes);
            loadWords(root, "suffixes", suffixes);
            loadAppellations(root);

            LOGGER.atInfo().log("Loaded elite name pools: %d prefixes, %d suffixes, %d appellations",
                    prefixes.size(), suffixes.size(), appellations.size());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load elite name pools from %s", POOL_RESOURCE);
        }
    }

    private void loadConfig(JsonObject root) {
        if (!root.has("config")) return;
        JsonObject config = root.getAsJsonObject("config");

        if (config.has("dedup_window")) {
            dedupWindow = config.get("dedup_window").getAsInt();
        }
        if (config.has("title_rules")) {
            JsonObject titleRules = config.getAsJsonObject("title_rules");
            if (titleRules.has("rare")) {
                JsonElement rareEl = titleRules.get("rare");
                if (rareEl.isJsonPrimitive() && rareEl.getAsJsonPrimitive().isNumber()) {
                    rareTitleChance = rareEl.getAsDouble();
                }
            }
        }
    }

    /**
     * Load prefix or suffix word entries from the named JSON array.
     * Note: faction_bias field is intentionally skipped (deferred feature).
     */
    private void loadWords(JsonObject root, String arrayKey, List<MobNameWord> target) {
        if (!root.has(arrayKey)) return;
        JsonArray arr = root.getAsJsonArray(arrayKey);
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            target.add(new MobNameWord(
                    obj.get("word").getAsString(),
                    obj.get("category").getAsString(),
                    MobNameRarity.fromString(obj.get("min_rarity").getAsString()),
                    obj.get("source").getAsString()
            ));
        }
    }

    private void loadAppellations(JsonObject root) {
        if (!root.has("appellations")) return;
        JsonArray arr = root.getAsJsonArray("appellations");
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            appellations.add(new MobNameAppellation(
                    obj.get("title").getAsString(),
                    obj.get("category").getAsString(),
                    MobNameRarity.fromString(obj.get("min_rarity").getAsString()),
                    obj.get("source").getAsString()
            ));
        }
    }

    // ── Generation ───────────────────────────────────────────────────────

    /**
     * Generate an elite name for the given encounter tier using ThreadLocalRandom.
     * Returns null for NORMAL tier (no name).
     */
    @Nullable
    public String generate(EncounterTier tier) {
        return generate(tier, ThreadLocalRandom.current());
    }

    /**
     * Generate an elite name for the given encounter tier using the supplied Random.
     * Returns null for NORMAL tier (no name).
     */
    @Nullable
    public String generate(EncounterTier tier, Random random) {
        if (tier == EncounterTier.NORMAL) {
            return null;
        }

        MobNameRarity rarity = MobNameRarity.fromTierOrdinal(tier.ordinal());

        List<MobNameWord> eligiblePrefixes = filterWords(prefixes, rarity);
        List<MobNameWord> eligibleSuffixes = filterWords(suffixes, rarity);

        if (eligiblePrefixes.isEmpty() || eligibleSuffixes.isEmpty()) {
            LOGGER.atWarning().log("Insufficient name pool entries for rarity %s (prefixes=%d, suffixes=%d)",
                    rarity, eligiblePrefixes.size(), eligibleSuffixes.size());
            return null;
        }

        List<MobNameAppellation> eligibleTitles = filterAppellations(appellations, rarity);

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            String fullName = buildName(eligiblePrefixes, eligibleSuffixes, eligibleTitles, rarity, random);

            // Dedup check
            if (!recentNames.contains(fullName)) {
                recordName(fullName);
                return fullName;
            }
        }

        // Exhausted retries: accept whatever we get
        String fallback = buildName(eligiblePrefixes, eligibleSuffixes, eligibleTitles, rarity, random);
        recordName(fallback);
        return fallback;
    }

    // ── Filtering ────────────────────────────────────────────────────────

    private List<MobNameWord> filterWords(List<MobNameWord> pool, MobNameRarity rarity) {
        List<MobNameWord> result = new ArrayList<>();
        for (MobNameWord word : pool) {
            if (word.minRarity().rank() <= rarity.rank()) {
                result.add(word);
            }
        }
        return result;
    }

    private List<MobNameAppellation> filterAppellations(List<MobNameAppellation> pool, MobNameRarity rarity) {
        List<MobNameAppellation> result = new ArrayList<>();
        for (MobNameAppellation app : pool) {
            if (app.minRarity().rank() <= rarity.rank()) {
                result.add(app);
            }
        }
        return result;
    }

    // ── Name construction ────────────────────────────────────────────────

    private String buildName(List<MobNameWord> prefixPool, List<MobNameWord> suffixPool,
                             List<MobNameAppellation> titlePool, MobNameRarity rarity, Random random) {
        MobNameWord prefix = prefixPool.get(random.nextInt(prefixPool.size()));
        MobNameWord suffix = suffixPool.get(random.nextInt(suffixPool.size()));
        String baseName = prefix.word() + suffix.word().toLowerCase();

        if (shouldAddTitle(rarity, random) && !titlePool.isEmpty()) {
            MobNameAppellation title = titlePool.get(random.nextInt(titlePool.size()));
            return baseName + " " + title.title();
        }
        return baseName;
    }

    // ── Title rules ──────────────────────────────────────────────────────

    private boolean shouldAddTitle(MobNameRarity rarity, Random random) {
        return switch (rarity) {
            case UNCOMMON -> false;
            case RARE -> random.nextDouble() < rareTitleChance;
            case EPIC, LEGENDARY -> true;
        };
    }

    // ── Dedup ────────────────────────────────────────────────────────────

    private void recordName(String name) {
        recentNames.addLast(name);
        while (recentNames.size() > dedupWindow) {
            recentNames.removeFirst();
        }
    }

    // ── Accessors ────────────────────────────────────────────────────────

    public int getPrefixCount() {
        return prefixes.size();
    }

    public int getSuffixCount() {
        return suffixes.size();
    }

    public int getAppellationCount() {
        return appellations.size();
    }
}
