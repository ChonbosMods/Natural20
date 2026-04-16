package com.chonbosmods.loot.mob.naming;

import com.chonbosmods.progression.DifficultyTier;
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

    // Rarity weighting: higher-tier words are favored at higher-tier mobs
    private boolean rarityWeightingEnabled = false;
    private int weightExactMatch = 4;
    private int weightOneBelow = 3;
    private int weightTwoBelow = 2;
    private int weightThreeBelow = 1;

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

            for (MobNameRarity r : MobNameRarity.values()) {
                int p = (int) prefixes.stream()
                        .filter(w -> w.minRarity().rank() <= r.rank()
                                && (w.maxRarity() != null ? w.maxRarity().rank() : w.minRarity().rank()) >= r.rank())
                        .count();
                int s = (int) suffixes.stream()
                        .filter(w -> w.minRarity().rank() <= r.rank()
                                && (w.maxRarity() != null ? w.maxRarity().rank() : w.minRarity().rank()) >= r.rank())
                        .count();
                LOGGER.atInfo().log("Name pool band %s: %d prefixes, %d suffixes", r, p, s);
            }
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
        if (config.has("rarity_weighting")) {
            JsonObject rw = config.getAsJsonObject("rarity_weighting");
            rarityWeightingEnabled = rw.has("enabled") && rw.get("enabled").getAsBoolean();
            if (rw.has("weights")) {
                JsonObject w = rw.getAsJsonObject("weights");
                if (w.has("exact_match")) weightExactMatch = w.get("exact_match").getAsInt();
                if (w.has("one_below")) weightOneBelow = w.get("one_below").getAsInt();
                if (w.has("two_below")) weightTwoBelow = w.get("two_below").getAsInt();
                if (w.has("three_below")) weightThreeBelow = w.get("three_below").getAsInt();
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
            MobNameRarity maxRarity = obj.has("max_rarity")
                    ? MobNameRarity.fromString(obj.get("max_rarity").getAsString())
                    : null;
            target.add(new MobNameWord(
                    obj.get("word").getAsString(),
                    obj.get("category").getAsString(),
                    MobNameRarity.fromString(obj.get("min_rarity").getAsString()),
                    maxRarity,
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
     * Generate an elite name for the given difficulty tier using ThreadLocalRandom.
     * Returns null if {@code difficulty} is null.
     */
    @Nullable
    public String generate(DifficultyTier difficulty) {
        return generate(difficulty, ThreadLocalRandom.current());
    }

    /**
     * Generate an elite name for the given difficulty tier using the supplied Random.
     * Returns null if {@code difficulty} is null.
     */
    @Nullable
    public String generate(DifficultyTier difficulty, Random random) {
        if (difficulty == null) return null;
        MobNameRarity rarity = rarityFor(difficulty);

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

    private MobNameRarity rarityFor(DifficultyTier d) {
        return switch (d) {
            case UNCOMMON  -> MobNameRarity.UNCOMMON;
            case RARE      -> MobNameRarity.RARE;
            case EPIC      -> MobNameRarity.EPIC;
            case LEGENDARY -> MobNameRarity.LEGENDARY;
        };
    }

    // ── Filtering ────────────────────────────────────────────────────────

    private List<MobNameWord> filterWords(List<MobNameWord> pool, MobNameRarity rarity) {
        List<MobNameWord> result = new ArrayList<>();
        for (MobNameWord word : pool) {
            MobNameRarity effectiveMax = (word.maxRarity() != null) ? word.maxRarity() : word.minRarity();
            if (word.minRarity().rank() <= rarity.rank()
                    && effectiveMax.rank() >= rarity.rank()) {
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
        MobNameWord prefix = pickWord(prefixPool, rarity, random);
        MobNameWord suffix = pickWord(suffixPool, rarity, random);
        String baseName = prefix.word() + suffix.word().toLowerCase();

        if (shouldAddTitle(rarity, random) && !titlePool.isEmpty()) {
            MobNameAppellation title = pickAppellation(titlePool, rarity, random);
            return baseName + " " + title.title();
        }
        return baseName;
    }

    /** Pick a word using rarity weighting (if enabled) or uniform random. */
    private MobNameWord pickWord(List<MobNameWord> pool, MobNameRarity mobRarity, Random random) {
        if (!rarityWeightingEnabled) {
            return pool.get(random.nextInt(pool.size()));
        }
        int totalWeight = 0;
        for (MobNameWord w : pool) {
            totalWeight += weightForDistance(mobRarity.rank() - w.minRarity().rank());
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (MobNameWord w : pool) {
            cumulative += weightForDistance(mobRarity.rank() - w.minRarity().rank());
            if (roll < cumulative) return w;
        }
        return pool.get(pool.size() - 1);
    }

    /** Pick an appellation using rarity weighting (if enabled) or uniform random. */
    private MobNameAppellation pickAppellation(List<MobNameAppellation> pool, MobNameRarity mobRarity, Random random) {
        if (!rarityWeightingEnabled) {
            return pool.get(random.nextInt(pool.size()));
        }
        int totalWeight = 0;
        for (MobNameAppellation a : pool) {
            totalWeight += weightForDistance(mobRarity.rank() - a.minRarity().rank());
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (MobNameAppellation a : pool) {
            cumulative += weightForDistance(mobRarity.rank() - a.minRarity().rank());
            if (roll < cumulative) return a;
        }
        return pool.get(pool.size() - 1);
    }

    /** Map the distance between mob rarity and word rarity to a weight. */
    private int weightForDistance(int distance) {
        return switch (distance) {
            case 0 -> weightExactMatch;
            case 1 -> weightOneBelow;
            case 2 -> weightTwoBelow;
            default -> weightThreeBelow;
        };
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
