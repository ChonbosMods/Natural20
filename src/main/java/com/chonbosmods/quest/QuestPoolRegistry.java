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
import java.util.List;
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
}
