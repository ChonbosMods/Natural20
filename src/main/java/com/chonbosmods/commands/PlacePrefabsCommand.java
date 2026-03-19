package com.chonbosmods.commands;

import com.google.common.flogger.FluentLogger;
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

import com.hypixel.hytale.server.core.prefab.PrefabStore;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public class PlacePrefabsCommand extends AbstractPlayerCommand {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private static final int SPACING = 100;
    private static final Set<String> CATEGORIES = Set.of("Npc", "Dungeon", "Monuments");

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

        placePrefabs(world, blockPos, prefabs);
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
                                 groupKey = relative.getName(0) + "/" + relative.getName(1);
                             } else {
                                 groupKey = relative.getName(0).toString();
                             }
                             result.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(p);
                         });
                } catch (IOException e) {
                    logger.atWarning().withCause(e).log("Failed to walk prefab directory: %s", categoryDir);
                }
            }
        }

        return result;
    }

    /**
     * Place all enumerated prefabs in a grid layout starting at the given position.
     * Stub: empty body until implemented.
     */
    private void placePrefabs(World world, Vector3i origin, Map<String, List<Path>> prefabs) {
    }
}
