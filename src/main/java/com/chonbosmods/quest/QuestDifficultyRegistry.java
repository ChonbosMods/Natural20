package com.chonbosmods.quest;

import com.chonbosmods.quest.model.DifficultyConfig;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Loads {@link DifficultyConfig} entries from {@code quests/difficulty/*.json}
 * on the classpath. Fails loudly: missing files, parse errors, invalid values,
 * and an empty registry all throw at load time. There are no silent fallbacks.
 */
public class QuestDifficultyRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_PREFIX = "quests/difficulty/";
    private static final List<String> DIFFICULTY_IDS = List.of("easy", "medium", "hard");

    private final Map<String, DifficultyConfig> configs = new LinkedHashMap<>();
    private final Gson gson = new Gson();

    /**
     * Load all difficulty configs from the classpath. Throws if any expected
     * file is missing, fails to parse, fails validation, or if the resulting
     * map is empty.
     */
    public void loadAll() {
        configs.clear();
        for (String id : DIFFICULTY_IDS) {
            String resource = CLASSPATH_PREFIX + id + ".json";
            DifficultyConfig config = loadOne(resource, id);
            configs.put(config.id(), config);
        }
        if (configs.isEmpty()) {
            throw new IllegalStateException(
                "QuestDifficultyRegistry loaded zero configs from " + CLASSPATH_PREFIX);
        }
        LOGGER.atInfo().log("Loaded %d quest difficulty configs: %s",
            configs.size(), configs.keySet());
    }

    private DifficultyConfig loadOne(String resource, String expectedId) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException(
                    "QuestDifficultyRegistry missing required classpath resource: " + resource);
            }
            DifficultyConfig config;
            try (var reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                config = gson.fromJson(reader, DifficultyConfig.class);
            }
            if (config == null) {
                throw new IllegalStateException(
                    "QuestDifficultyRegistry parsed null config from " + resource);
            }
            validate(config, expectedId, resource);
            return config;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                "QuestDifficultyRegistry failed to load " + resource, e);
        }
    }

    private void validate(DifficultyConfig c, String expectedId, String resource) {
        if (c.id() == null || c.id().isEmpty()) {
            fail(resource, "<unknown>", "id must be non-empty");
        }
        if (!c.id().equals(expectedId)) {
            fail(resource, c.id(),
                "id '" + c.id() + "' does not match filename stem '" + expectedId + "'");
        }
        if (c.xpAmount() <= 0) {
            fail(resource, c.id(), "xpAmount must be > 0 (got " + c.xpAmount() + ")");
        }
        if (c.ilvlBonus() < 0) {
            fail(resource, c.id(), "ilvlBonus must be >= 0 (got " + c.ilvlBonus() + ")");
        }
        if (c.bossIlvlOffset() < 0) {
            fail(resource, c.id(),
                "bossIlvlOffset must be >= 0 (got " + c.bossIlvlOffset() + ")");
        }
        if (c.mobCountMultiplier() <= 0) {
            fail(resource, c.id(),
                "mobCountMultiplier must be > 0 (got " + c.mobCountMultiplier() + ")");
        }
        if (c.gatherCountMultiplier() <= 0) {
            fail(resource, c.id(),
                "gatherCountMultiplier must be > 0 (got " + c.gatherCountMultiplier() + ")");
        }
        if (c.rewardTierMin() == null || c.rewardTierMin().isEmpty()) {
            fail(resource, c.id(), "rewardTierMin must be non-empty");
        }
        if (c.rewardTierMax() == null || c.rewardTierMax().isEmpty()) {
            fail(resource, c.id(), "rewardTierMax must be non-empty");
        }
    }

    private static void fail(String resource, String id, String reason) {
        throw new IllegalStateException(
            "QuestDifficultyRegistry invalid config in " + resource
            + " (id=" + id + "): " + reason);
    }

    /**
     * Look up a difficulty by id. Returns {@code null} if no such id is loaded;
     * callers decide whether that's fatal.
     */
    @Nullable
    public DifficultyConfig get(String id) {
        return configs.get(id);
    }

    /**
     * Pick a difficulty uniformly at random. Throws {@link IllegalStateException}
     * if the registry is empty: there is no silent fallback.
     */
    public DifficultyConfig random(Random random) {
        if (configs.isEmpty()) {
            throw new IllegalStateException(
                "QuestDifficultyRegistry.random() called on empty registry "
                + "(loadAll not called or failed silently)");
        }
        List<DifficultyConfig> values = List.copyOf(configs.values());
        return values.get(random.nextInt(values.size()));
    }

    /** All loaded difficulty ids, in load order. For logging / diagnostics. */
    public Collection<String> ids() {
        return Collections.unmodifiableCollection(configs.keySet());
    }
}
