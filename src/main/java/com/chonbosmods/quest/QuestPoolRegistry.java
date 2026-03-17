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

    private final List<PoolEntry> gatherItems = new ArrayList<>();
    private final List<PoolEntry> hostileMobs = new ArrayList<>();

    public record PoolEntry(String id, String label) {}

    public void loadAll(@Nullable Path poolsDir) {
        if (poolsDir == null || !Files.isDirectory(poolsDir)) {
            LOGGER.atWarning().log("Pools directory not found: %s", poolsDir);
            return;
        }

        loadPool(poolsDir.resolve("gather_items.json"), "items", gatherItems);
        loadPool(poolsDir.resolve("hostile_mobs.json"), "mobs", hostileMobs);

        LOGGER.atInfo().log("Loaded pools: %d gather items, %d hostile mobs",
            gatherItems.size(), hostileMobs.size());
    }

    private void loadPool(Path file, String arrayKey, List<PoolEntry> target) {
        if (!Files.exists(file)) {
            LOGGER.atWarning().log("Pool file not found: %s", file);
            return;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray(arrayKey);
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                target.add(new PoolEntry(
                    obj.get("id").getAsString(),
                    obj.get("label").getAsString()
                ));
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load pool: %s", file);
        }
    }

    public PoolEntry randomGatherItem(Random random) {
        if (gatherItems.isEmpty()) return new PoolEntry("Hytale:Stone", "stone");
        return gatherItems.get(random.nextInt(gatherItems.size()));
    }

    public PoolEntry randomHostileMob(Random random) {
        if (hostileMobs.isEmpty()) return new PoolEntry("Hytale:Trork_Grunt", "Trork Grunt");
        return hostileMobs.get(random.nextInt(hostileMobs.size()));
    }

    public List<PoolEntry> getGatherItems() { return gatherItems; }
    public List<PoolEntry> getHostileMobs() { return hostileMobs; }
}
