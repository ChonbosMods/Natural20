package com.chonbosmods.commands;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.util.PrefabUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class PlacePrefabsCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PlacePrefabs");

    private static final int SPACING = 30;
    private static final int SETTLE_TICKS = 5;

    private record PlacementEntry(Path path, IPrefabBuffer buffer, Vector3i position, String groupKey) {}

    private final RequiredArg<String> filterArg =
            withRequiredArg("filter", "'help' for categories, 'all' for everything, or filter e.g. Kweebec", ArgTypes.STRING);

    public PlacePrefabsCommand() {
        super("placeprefabs", "Place vanilla prefabs in a grid");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String filterRaw = filterArg.get(context);
        LOGGER.atInfo().log("placeprefabs called with filter='%s'", filterRaw);

        if (filterRaw.equalsIgnoreCase("help")) {
            showHelp(context);
            return;
        }

        String filter = filterRaw.equalsIgnoreCase("all") ? null : filterRaw;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        int originX = (int) pos.getX();
        int originZ = (int) pos.getZ();

        Map<String, List<Path>> prefabs = enumeratePrefabs(filter);
        LOGGER.atInfo().log("enumeratePrefabs returned %d groups", prefabs.size());
        if (prefabs.isEmpty()) {
            context.sendMessage(Message.raw("No prefabs found" + (filter != null ? " matching '" + filter + "'" : "") + "."));
            if (filter != null) {
                context.sendMessage(Message.raw("Use --help to see available categories."));
            }
            return;
        }

        int total = prefabs.values().stream().mapToInt(List::size).sum();
        context.sendMessage(Message.raw("Found " + total + " prefabs in " + prefabs.size() + " groups. Loading buffers..."));

        List<PlacementEntry> entries = buildPlacementList(prefabs, originX, originZ, context);
        if (entries.isEmpty()) {
            context.sendMessage(Message.raw("No prefabs could be loaded."));
            return;
        }

        context.sendMessage(Message.raw("Placing " + entries.size() + " prefabs..."));
        placeNext(world, entries, 0, 0, store, context, new Random());
    }

    private void showHelp(CommandContext context) {
        Map<String, List<Path>> allPrefabs = enumerateAllPrefabs();
        LOGGER.atInfo().log("Help: enumerateAllPrefabs returned %d groups", allPrefabs.size());

        // Build category -> subcategories, output as compact lines
        TreeMap<String, List<String>> categories = new TreeMap<>();

        for (Map.Entry<String, List<Path>> entry : allPrefabs.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue().size();
            String[] parts = key.split("/", 2);
            String cat = parts[0];
            String sub = parts.length > 1 ? parts[1] : null;
            categories.computeIfAbsent(cat, k -> new ArrayList<>());
            if (sub != null) {
                categories.get(cat).add(sub + "(" + count + ")");
            }
        }

        context.sendMessage(Message.raw("--- Prefab Categories ---"));
        for (Map.Entry<String, List<String>> cat : categories.entrySet()) {
            String catName = cat.getKey();
            List<String> subs = cat.getValue();
            if (subs.isEmpty()) {
                context.sendMessage(Message.raw(catName));
            } else {
                // Join subcategories into one line per category
                context.sendMessage(Message.raw(catName + ": " + String.join(", ", subs)));
            }
        }

        context.sendMessage(Message.raw("--- Usage: /nat20 placeprefabs <filter> ---"));
        context.sendMessage(Message.raw("Examples: Kweebec, Dungeon/Sewer, Challenge_Gate, Feran"));
    }

    private Map<String, List<Path>> enumerateAllPrefabs() {
        TreeMap<String, List<Path>> result = new TreeMap<>();

        List<PrefabStore.AssetPackPrefabPath> allPaths = PrefabStore.get().getAllAssetPrefabPaths();
        LOGGER.atInfo().log("getAllAssetPrefabPaths returned %d paths", allPaths.size());
        for (PrefabStore.AssetPackPrefabPath assetPackPath : allPaths) {
            Path prefabsDir = assetPackPath.prefabsPath();
            LOGGER.atInfo().log("Checking prefab dir: %s exists=%b isDir=%b", prefabsDir, Files.exists(prefabsDir), Files.isDirectory(prefabsDir));
            if (!Files.isDirectory(prefabsDir)) continue;

            try (Stream<Path> paths = Files.walk(prefabsDir)) {
                paths.filter(p -> p.toString().endsWith(".prefab.json"))
                     .forEach(p -> {
                         Path relative = prefabsDir.relativize(p);
                         String groupKey;
                         if (relative.getNameCount() >= 3) {
                             groupKey = relative.getName(0).toString() + "/" + relative.getName(1).toString();
                         } else if (relative.getNameCount() >= 2) {
                             groupKey = relative.getName(0).toString();
                         } else {
                             groupKey = "Other";
                         }
                         result.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(p);
                     });
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to walk prefab directory: %s", prefabsDir);
            }
        }

        return result;
    }

    private Map<String, List<Path>> enumeratePrefabs(String filter) {
        Map<String, List<Path>> all = enumerateAllPrefabs();
        if (filter == null || filter.isEmpty()) {
            return all;
        }

        String lowerFilter = filter.toLowerCase();
        TreeMap<String, List<Path>> filtered = new TreeMap<>();

        for (Map.Entry<String, List<Path>> entry : all.entrySet()) {
            String key = entry.getKey();
            // Match against group key (e.g. "Npc/Kweebec") or individual path segments
            if (key.toLowerCase().contains(lowerFilter)) {
                filtered.put(key, entry.getValue());
            } else {
                // Check individual prefab paths for the filter term
                List<Path> matching = new ArrayList<>();
                for (Path p : entry.getValue()) {
                    if (p.toString().toLowerCase().contains(lowerFilter)) {
                        matching.add(p);
                    }
                }
                if (!matching.isEmpty()) {
                    filtered.put(key, matching);
                }
            }
        }

        return filtered;
    }

    private List<PlacementEntry> buildPlacementList(Map<String, List<Path>> prefabs,
                                                     int originX, int originZ,
                                                     CommandContext context) {
        List<PlacementEntry> entries = new ArrayList<>();
        int groupIndex = 0;

        for (Map.Entry<String, List<Path>> group : prefabs.entrySet()) {
            String groupKey = group.getKey();
            List<Path> paths = group.getValue();

            for (int i = 0; i < paths.size(); i++) {
                try {
                    IPrefabBuffer buffer = PrefabBufferUtil.getCached(paths.get(i));
                    int x = originX + i * SPACING;
                    int z = originZ + groupIndex * SPACING;
                    int y = buffer.getAnchorY() - buffer.getMinY();
                    entries.add(new PlacementEntry(paths.get(i), buffer, new Vector3i(x, y, z), groupKey));
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to load prefab buffer: %s", paths.get(i));
                }
            }

            context.sendMessage(Message.raw("  " + groupKey + ": " + paths.size() + " prefabs"));
            groupIndex++;
        }

        return entries;
    }

    private void placeNext(World world, List<PlacementEntry> entries, int index, int placedSoFar,
                           Store<EntityStore> store, CommandContext context, Random random) {
        if (index >= entries.size()) {
            context.sendMessage(Message.raw("Done! Placed " + placedSoFar + " prefabs."));
            return;
        }

        PlacementEntry entry = entries.get(index);
        IPrefabBuffer buffer = entry.buffer();
        Vector3i pos = entry.position();

        int minChunkX = ChunkUtil.chunkCoordinate(pos.getX() + buffer.getMinX());
        int minChunkZ = ChunkUtil.chunkCoordinate(pos.getZ() + buffer.getMinZ());
        int maxChunkX = ChunkUtil.chunkCoordinate(pos.getX() + buffer.getMaxX());
        int maxChunkZ = ChunkUtil.chunkCoordinate(pos.getZ() + buffer.getMaxZ());

        List<CompletableFuture<?>> chunkFutures = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ChunkUtil.indexChunk(cx, cz);
                chunkFutures.add(world.getNonTickingChunkAsync(key));
            }
        }

        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((result, error) -> {
                    deferTicks(world, SETTLE_TICKS, () -> {
                        int placed = placedSoFar;

                        if (error != null) {
                            LOGGER.atWarning().withCause(error).log("Chunk loading timed out for prefab %d: %s", index, entry.path());
                        } else {
                            try {
                                PrefabUtil.paste(buffer, world, pos, Rotation.None, true, random, 0, store);
                                placed++;
                            } catch (Exception e) {
                                LOGGER.atWarning().withCause(e).log("Failed to place prefab %d: %s", index, entry.path());
                            }
                        }

                        int next = index + 1;
                        if (next % 50 == 0) {
                            context.sendMessage(Message.raw("Progress: " + placed + "/" + entries.size()));
                        }

                        final int placedFinal = placed;
                        deferTicks(world, SETTLE_TICKS, () ->
                                placeNext(world, entries, next, placedFinal, store, context, random));
                    });
                });
    }

    private void deferTicks(World world, int ticks, Runnable action) {
        if (ticks <= 0) {
            world.execute(action);
        } else {
            world.execute(() -> deferTicks(world, ticks - 1, action));
        }
    }
}
