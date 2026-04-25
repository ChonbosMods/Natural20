package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.AffixType;
import com.chonbosmods.loot.def.LootRuleEntry;
import com.chonbosmods.loot.def.Nat20RarityDef;
import com.chonbosmods.progression.DifficultyTier;
import com.google.common.flogger.FluentLogger;
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
import java.util.*;
import java.util.stream.Stream;

public class Nat20RarityRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_DIR = "loot/rarities/";
    private static final String[] BUILTIN_FILES = {
        "Common.json", "Uncommon.json", "Rare.json", "Epic.json", "Legendary.json"
    };

    private final Map<String, Nat20RarityDef> raritiesById = new LinkedHashMap<>();
    private final List<Nat20RarityDef> raritiesByValue = new ArrayList<>();

    public void loadAll(@Nullable Path overrideDir) {
        for (String file : BUILTIN_FILES) {
            loadClasspathFile(CLASSPATH_DIR + file);
        }
        if (overrideDir != null && Files.isDirectory(overrideDir)) {
            try (Stream<Path> files = Files.list(overrideDir)) {
                files.filter(p -> p.toString().endsWith(".json")).forEach(this::loadFile);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to load rarity overrides from %s", overrideDir);
            }
        }
        raritiesByValue.clear();
        raritiesByValue.addAll(raritiesById.values());
        raritiesByValue.sort(Comparator.comparingInt(Nat20RarityDef::qualityValue));
        LOGGER.atInfo().log("Loaded %d rarity definitions", raritiesById.size());
    }

    private void loadClasspathFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.atWarning().log("Rarity file not found on classpath: %s", path);
                return;
            }
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            String id = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
            Nat20RarityDef def = parseRarity(id, obj);
            raritiesById.put(id, def);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load classpath rarity: %s", path);
        }
    }

    private void loadFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            String id = file.getFileName().toString().replace(".json", "");
            Nat20RarityDef def = parseRarity(id, obj);
            raritiesById.put(id, def);
            LOGGER.atInfo().log("Loaded rarity override: %s from %s", id, file.getFileName());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse rarity file: %s", file);
        }
    }

    private Nat20RarityDef parseRarity(String id, JsonObject obj) {
        List<LootRuleEntry> lootRules = new ArrayList<>();
        if (obj.has("LootRules")) {
            for (JsonElement el : obj.getAsJsonArray("LootRules")) {
                JsonObject rule = el.getAsJsonObject();
                lootRules.add(new LootRuleEntry(
                    AffixType.valueOf(rule.get("Type").getAsString()),
                    rule.get("Count").getAsInt(),
                    rule.get("Probability").getAsDouble()
                ));
            }
        }
        return new Nat20RarityDef(
            id,
            obj.get("QualityValue").getAsInt(),
            obj.get("Color").getAsString(),
            obj.has("DisplayName") ? obj.get("DisplayName").getAsString() : "server.nat20.rarity." + id.toLowerCase(),
            obj.get("BaseWeight").getAsInt(),
            obj.get("MaxAffixes").getAsInt(),
            obj.get("MaxSockets").getAsInt(),
            obj.has("TooltipTexture") ? obj.get("TooltipTexture").getAsString() : "",
            obj.has("TooltipArrowTexture") ? obj.get("TooltipArrowTexture").getAsString() : "",
            obj.has("SlotTexture") ? obj.get("SlotTexture").getAsString() : "",
            lootRules
        );
    }

    public Nat20RarityDef get(String id) {
        return raritiesById.get(id);
    }

    public Collection<Nat20RarityDef> getAll() {
        return raritiesById.values();
    }

    public List<Nat20RarityDef> getAllSorted() {
        return raritiesByValue;
    }

    public Nat20RarityDef selectRandom(Random random) {
        int totalWeight = 0;
        for (var def : raritiesById.values()) {
            totalWeight += def.baseWeight();
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (var def : raritiesById.values()) {
            cumulative += def.baseWeight();
            if (roll < cumulative) return def;
        }
        return raritiesByValue.getLast();
    }

    /**
     * Select a random rarity from those whose qualityValue falls within [minTier, maxTier].
     * Uses base weight for weighted selection among the filtered pool.
     * Falls back to unclamped selection if no rarities match the tier range.
     */
    public Nat20RarityDef selectRandom(Random random, int minTier, int maxTier) {
        List<Nat20RarityDef> pool = new ArrayList<>();
        int totalWeight = 0;
        for (var def : raritiesById.values()) {
            if (def.qualityValue() >= minTier && def.qualityValue() <= maxTier) {
                pool.add(def);
                totalWeight += def.baseWeight();
            }
        }
        if (pool.isEmpty()) {
            LOGGER.atWarning().log("No rarities match tier range [%d, %d], falling back to unclamped selection",
                minTier, maxTier);
            return selectRandom(random);
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (var def : pool) {
            cumulative += def.baseWeight();
            if (roll < cumulative) return def;
        }
        return pool.getLast();
    }

    /**
     * Difficulty-biased rarity selection for Nat20 mob drops.
     *
     * <p>Starts from the default per-rarity base weights (filtered to the
     * [minTier, maxTier] window), then applies two layered biases keyed off
     * {@link DifficultyTier}:
     * <ul>
     *   <li>difficulty >= RARE: multiply Uncommon / Rare / Epic / Legendary
     *       weights by 1.5x.</li>
     *   <li>difficulty >= EPIC: zero Common's weight and redistribute its
     *       original 300-point share into Uncommon (+100), Rare (+150),
     *       Epic (+40), Legendary (+10).</li>
     * </ul>
     *
     * <p>Unknown/null difficulty (chest loot, quest rewards, UNCOMMON mobs)
     * behaves identically to {@link #selectRandom(Random, int, int)}.
     *
     * <p>See {@code docs/plans/2026-04-21-mob-loot-tuning-design.md} §4 for
     * the probability tables this reproduces.
     */
    public Nat20RarityDef selectRandomForDifficulty(Random random, int minTier, int maxTier,
                                                     @Nullable DifficultyTier difficulty) {
        if (difficulty == null || difficulty == DifficultyTier.UNCOMMON) {
            return selectRandom(random, minTier, maxTier);
        }

        boolean zeroCommon = difficulty == DifficultyTier.EPIC
                || difficulty == DifficultyTier.LEGENDARY;

        List<Nat20RarityDef> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0.0;

        for (var def : raritiesById.values()) {
            if (def.qualityValue() < minTier || def.qualityValue() > maxTier) continue;
            double w = def.baseWeight();
            boolean isCommon = def.qualityValue() == 1;
            if (isCommon && zeroCommon) {
                w = 0.0;
            } else if (!isCommon) {
                w *= 1.5;
            }
            // EPIC/LEGENDARY: redistribute Common's original 250 weight upward.
            if (zeroCommon) {
                // 80 + 130 + 30 + 10 = 250 = Common's BaseWeight in Common.json; keep in sync.
                switch (def.qualityValue()) {
                    case 2 -> w += 80.0;  // Uncommon
                    case 3 -> w += 130.0; // Rare
                    case 4 -> w += 30.0;  // Epic
                    case 5 -> w += 10.0;  // Legendary
                    default -> {}
                }
            }
            if (w <= 0.0) continue;
            pool.add(def);
            weights.add(w);
            totalWeight += w;
        }

        if (pool.isEmpty() || totalWeight <= 0.0) {
            LOGGER.atWarning().log("selectRandomForDifficulty: empty pool for tierRange=[%d,%d] difficulty=%s, falling back",
                    minTier, maxTier, difficulty);
            return selectRandom(random, minTier, maxTier);
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (int i = 0; i < pool.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) return pool.get(i);
        }
        return pool.getLast();
    }

    public int getLoadedCount() {
        return raritiesById.size();
    }
}
