package com.chonbosmods.quest;

import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
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

    /** Entry with id + label for items/mobs. */
    public record ItemEntry(String id, String label) {}

    /** Entry with value + plural flag for narrative pools. */
    public record NarrativeEntry(String value, boolean plural) {}

    private final List<ItemEntry> gatherItems = new ArrayList<>();
    private final List<ItemEntry> hostileMobs = new ArrayList<>();
    private final List<String> questActions = new ArrayList<>();
    private final List<NarrativeEntry> questFocuses = new ArrayList<>();
    private final List<NarrativeEntry> questStakes = new ArrayList<>();
    private final List<NarrativeEntry> questThreats = new ArrayList<>();
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
    private final Map<String, List<String>> targetNpcResponses = new HashMap<>();
    private final Map<String, String> situationTones = new HashMap<>();

    public void loadAll(@Nullable Path poolsDir) {
        if (poolsDir == null || !Files.isDirectory(poolsDir)) {
            LOGGER.atWarning().log("Pools directory not found: %s", poolsDir);
            return;
        }

        loadItemPool(poolsDir.resolve("gather_items.json"), "items", gatherItems);
        loadItemPool(poolsDir.resolve("hostile_mobs.json"), "mobs", hostileMobs);
        loadStringPool(poolsDir.resolve("quest_actions.json"), questActions);
        loadNarrativePool(poolsDir.resolve("quest_focuses.json"), questFocuses);
        loadNarrativePool(poolsDir.resolve("quest_stakes.json"), questStakes);
        loadNarrativePool(poolsDir.resolve("quest_threats.json"), questThreats);
        loadStringPool(poolsDir.resolve("quest_origins.json"), questOrigins);
        loadStringPool(poolsDir.resolve("quest_time_pressures.json"), questTimePressures);
        loadStringPool(poolsDir.resolve("quest_reward_hints.json"), questRewardHints);

        loadTonedResponses(poolsDir.resolve("responses_accept.json"), acceptResponses);
        loadTonedResponses(poolsDir.resolve("responses_decline.json"), declineResponses);
        loadTonedResponses(poolsDir.resolve("responses_counter_accept.json"), counterAcceptResponses);
        loadTonedResponses(poolsDir.resolve("responses_counter_decline.json"), counterDeclineResponses);
        loadTonedResponses(poolsDir.resolve("responses_counter_stat_pass.json"), counterStatPassResponses);
        loadTonedResponses(poolsDir.resolve("responses_counter_stat_fail.json"), counterStatFailResponses);
        loadTonedResponses(poolsDir.resolve("responses_stat_check.json"), statCheckResponses);
        loadTonedResponses(poolsDir.resolve("responses_target_npc.json"), targetNpcResponses);
        loadSituationTones(poolsDir.resolve("situation_tones.json"));

        LOGGER.atInfo().log("Loaded pools: %d items, %d mobs, %d actions, %d focuses, %d stakes, %d threats, %d origins, %d pressures, %d rewards",
            gatherItems.size(), hostileMobs.size(), questActions.size(), questFocuses.size(),
            questStakes.size(), questThreats.size(), questOrigins.size(),
            questTimePressures.size(), questRewardHints.size());
    }

    private void loadItemPool(Path file, String arrayKey, List<ItemEntry> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray(arrayKey);
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                target.add(new ItemEntry(obj.get("id").getAsString(), obj.get("label").getAsString()));
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool: %s", file);
        }
    }

    private void loadNarrativePool(Path file, List<NarrativeEntry> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("values");
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    target.add(new NarrativeEntry(
                        obj.get("value").getAsString(),
                        obj.has("plural") && obj.get("plural").getAsBoolean()
                    ));
                } else {
                    // Backwards compat: plain string = singular
                    target.add(new NarrativeEntry(el.getAsString(), false));
                }
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool: %s", file);
        }
    }

    private void loadStringPool(Path file, List<String> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("values");
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) {
                    target.add(el.getAsString());
                } else if (el.isJsonObject()) {
                    target.add(el.getAsJsonObject().get("value").getAsString());
                }
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool: %s", file);
        }
    }

    public ItemEntry randomGatherItem(Random random) {
        if (gatherItems.isEmpty()) return new ItemEntry("Hytale:Stone", "stone");
        return gatherItems.get(random.nextInt(gatherItems.size()));
    }

    public ItemEntry randomHostileMob(Random random) {
        if (hostileMobs.isEmpty()) return new ItemEntry("Hytale:Trork_Grunt", "Trork Grunt");
        return hostileMobs.get(random.nextInt(hostileMobs.size()));
    }

    public String randomAction(Random random) {
        if (questActions.isEmpty()) return "investigate the situation";
        return questActions.get(random.nextInt(questActions.size()));
    }

    public NarrativeEntry randomFocus(Random random) {
        if (questFocuses.isEmpty()) return new NarrativeEntry("area", false);
        return questFocuses.get(random.nextInt(questFocuses.size()));
    }

    public NarrativeEntry randomStakes(Random random) {
        if (questStakes.isEmpty()) return new NarrativeEntry("everyone here", true);
        return questStakes.get(random.nextInt(questStakes.size()));
    }

    public NarrativeEntry randomThreat(Random random) {
        if (questThreats.isEmpty()) return new NarrativeEntry("growing danger", false);
        return questThreats.get(random.nextInt(questThreats.size()));
    }

    public @Nullable String randomOrigin(Random random) {
        if (questOrigins.isEmpty()) return null;
        return questOrigins.get(random.nextInt(questOrigins.size()));
    }

    public @Nullable String randomTimePressure(Random random) {
        if (questTimePressures.isEmpty()) return null;
        return questTimePressures.get(random.nextInt(questTimePressures.size()));
    }

    public @Nullable String randomRewardHint(Random random) {
        if (questRewardHints.isEmpty()) return null;
        return questRewardHints.get(random.nextInt(questRewardHints.size()));
    }

    public String getToneForSituation(String situationId) {
        return situationTones.getOrDefault(situationId, "somber");
    }

    /**
     * Pick an accept response: tries situation-specific first, then falls back to tone.
     */
    public String randomAcceptResponse(String situationId, String tone, Random random) {
        List<String> pool = acceptResponses.get(situationId);
        if (pool == null || pool.isEmpty()) {
            pool = acceptResponses.getOrDefault(tone, acceptResponses.get("somber"));
        }
        if (pool == null || pool.isEmpty()) return "I'll help.";
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Pick a decline response: tries situation-specific first, then falls back to tone.
     */
    public String randomDeclineResponse(String situationId, String tone, Random random) {
        List<String> pool = declineResponses.get(situationId);
        if (pool == null || pool.isEmpty()) {
            pool = declineResponses.getOrDefault(tone, declineResponses.get("somber"));
        }
        if (pool == null || pool.isEmpty()) return "I can't help right now.";
        return pool.get(random.nextInt(pool.size()));
    }

    public String randomCounterAccept(String situationId, String tone, Random random) {
        List<String> pool = counterAcceptResponses.get(tone);
        if (pool == null || pool.isEmpty()) return "Thank you.";
        return pool.get(random.nextInt(pool.size()));
    }

    public String randomCounterDecline(String situationId, String tone, Random random) {
        List<String> pool = counterDeclineResponses.get(tone);
        if (pool == null || pool.isEmpty()) return "I understand.";
        return pool.get(random.nextInt(pool.size()));
    }

    public String randomCounterStatPass(String tone, Random random) {
        List<String> pool = counterStatPassResponses.get(tone);
        if (pool == null || pool.isEmpty()) return "Impressive. I'm glad you're helping.";
        return pool.get(random.nextInt(pool.size()));
    }

    public String randomCounterStatFail(String tone, Random random) {
        List<String> pool = counterStatFailResponses.get(tone);
        if (pool == null || pool.isEmpty()) return "Well, we'll manage somehow.";
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Pick a target NPC dialogue line for TALK_TO_NPC objectives.
     */
    public String randomTargetNpcDialogue(String tone, Random random) {
        List<String> pool = targetNpcResponses.get(tone);
        if (pool == null || pool.isEmpty()) return "I know about the {quest_focus}. Here's what I can tell you.";
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Pick a stat-gated player response line for the given stat.
     */
    public String randomStatCheckResponse(String stat, Random random) {
        List<String> pool = statCheckResponses.get(stat);
        if (pool == null || pool.isEmpty()) return "I'll use my abilities to handle this.";
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Pick a random stat type for a stat-gated response option.
     */
    public String randomStatType(Random random) {
        String[] stats = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
        return stats[random.nextInt(stats.length)];
    }

    private void loadTonedResponses(Path file, Map<String, List<String>> target) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            // Support both "tones" and "stats" as root keys
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
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load responses: %s", file);
        }
    }

    private void loadSituationTones(Path file) {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject tones = root.getAsJsonObject("tones");
            for (var entry : tones.entrySet()) {
                situationTones.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load situation tones: %s", file);
        }
    }
}
