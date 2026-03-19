package com.chonbosmods.commands;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public class PlacePrefabsCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PlacePrefabs");

    private static final int SPACING = 100;
    private static final int BATCH_SIZE = 10;
    private static final Set<String> CATEGORIES = Set.of("Npc", "Dungeon", "Monuments");

    private record PlacementEntry(Path path, Vector3i position, String groupKey) {}

    public PlacePrefabsCommand() {
        super("placeprefabs", "Place all vanilla NPC/Dungeon/Monument prefabs in a grid");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3i blockPos = new Vector3i((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

        Map<String, List<Path>> prefabs = enumeratePrefabs();
        if (prefabs.isEmpty()) {
            context.sendMessage(Message.raw("No prefabs found."));
            return;
        }

        int total = prefabs.values().stream().mapToInt(List::size).sum();
        context.sendMessage(Message.raw("Found " + total + " prefabs in " + prefabs.size() + " categories. Placing..."));

        placePrefabs(world, blockPos, prefabs, store, context);
    }

    /**
     * Enumerate vanilla prefabs from asset packs, grouped by subcategory.
     * Scans Npc, Dungeon, and Monuments directories for .prefab.json files,
     * grouping by the first two path components relative to the prefabs root
     * (e.g. "Npc/Outlander").
     */
    private Map<String, List<Path>> enumeratePrefabs() {
        TreeMap<String, List<Path>> result = new TreeMap<>();

        List<PrefabStore.AssetPackPrefabPath> allPaths = PrefabStore.get().getAllAssetPrefabPaths();
        for (PrefabStore.AssetPackPrefabPath assetPackPath : allPaths) {
            Path prefabsDir = assetPackPath.prefabsPath();

            for (String category : CATEGORIES) {
                Path categoryDir = prefabsDir.resolve(category);
                if (!Files.isDirectory(categoryDir)) {
                    continue;
                }

                try (Stream<Path> paths = Files.walk(categoryDir)) {
                    paths.filter(p -> p.toString().endsWith(".prefab.json"))
                         .forEach(p -> {
                             Path relative = prefabsDir.relativize(p);
                             String groupKey;
                             if (relative.getNameCount() >= 3) {
                                 groupKey = relative.getName(0).toString() + "/" + relative.getName(1).toString();
                             } else {
                                 groupKey = relative.getName(0).toString();
                             }
                             result.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(p);
                         });
                } catch (IOException e) {
                    LOGGER.atWarning().withCause(e).log("Failed to walk prefab directory: %s", categoryDir);
                }
            }
        }

        return result;
    }

    /**
     * Place all enumerated prefabs in a grid layout starting at the given position.
     * Each group occupies one row along the +X axis; groups advance along the +Z axis.
     */
    private void placePrefabs(World world, Vector3i origin, Map<String, List<Path>> prefabs,
                              Store<EntityStore> store, CommandContext context) {
        List<PlacementEntry> entries = new ArrayList<>();
        int groupIndex = 0;

        for (Map.Entry<String, List<Path>> group : prefabs.entrySet()) {
            String groupKey = group.getKey();
            List<Path> paths = group.getValue();

            for (int i = 0; i < paths.size(); i++) {
                int x = origin.getX() + i * SPACING;
                int y = origin.getY();
                int z = origin.getZ() + groupIndex * SPACING;
                entries.add(new PlacementEntry(paths.get(i), new Vector3i(x, y, z), groupKey));
            }
            groupIndex++;
        }

        Random random = new Random();
        placeBatch(world, entries, 0, 0, store, context, random);
    }

    /**
     * Place a batch of prefabs on the world thread, then schedule the next batch.
     * Processes BATCH_SIZE entries per tick to avoid blocking the server.
     */
    private void placeBatch(World world, List<PlacementEntry> entries, int startIndex, int placedSoFar,
                            Store<EntityStore> store, CommandContext context, Random random) {
        world.execute(() -> {
            int[] placed = {placedSoFar};
            int end = Math.min(startIndex + BATCH_SIZE, entries.size());

            for (int i = startIndex; i < end; i++) {
                PlacementEntry entry = entries.get(i);
                try {
                    IPrefabBuffer buffer = PrefabBufferUtil.getCached(entry.path());
                    PrefabUtil.paste(buffer, world, entry.position(), Rotation.None, true, random, 0, store);
                    placed[0]++;
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to place prefab: %s", entry.path());
                }

                if ((i + 1) % 50 == 0) {
                    context.sendMessage(Message.raw("Progress: placed " + placed[0] + "/" + entries.size() + " prefabs..."));
                }
            }

            if (end < entries.size()) {
                placeBatch(world, entries, end, placed[0], store, context, random);
            } else {
                context.sendMessage(Message.raw("Done! Placed " + placed[0] + " prefabs."));
            }
        });
    }
}
